package service;

import java.util.HashMap;
import java.util.Map;
import model.Usuario;
import repository.UsuarioRepository;
import util.Seguranca;

/** Autenticação de usuários (persistidos no PostgreSQL) e controle de sessões (em memória). */
public class AutenticacaoService {
    // ====================================================================
    //  CREDENCIAL DE DEMONSTRAÇÃO (DEMO) — NÃO USE EM PRODUÇÃO.
    //  Só é usada para criar o primeiro administrador quando as variáveis
    //  de ambiente abaixo não estão definidas:
    //    CLINICA_ADMIN_LOGIN  e  CLINICA_ADMIN_SENHA
    //  Depois de criado, o administrador pode trocar a senha na tela de usuários.
    // ====================================================================
    private static final String DEMO_LOGIN = "Vitallis";
    private static final String DEMO_SENHA = "Vitallis2026";
    public static final int LOGIN_MAX = 40;
    public static final int SENHA_MAX = 72;
    public static final long INATIVIDADE_MS = 10 * 60 * 1000L;

    private final UsuarioRepository repository;
    private final Map<String, Sessao> sessoes = new HashMap<>();
    // hash descartável: mantém o tempo de resposta parecido quando o login não existe
    private final String hashFalso = Seguranca.gerarHash("senha-inexistente");

    private static final class Sessao {
        final int usuarioId;
        long ultimoUso;

        Sessao(int usuarioId, long ultimoUso) {
            this.usuarioId = usuarioId;
            this.ultimoUso = ultimoUso;
        }
    }

    public AutenticacaoService(UsuarioRepository repository) {
        this.repository = repository;
    }

    /**
     * Cria o administrador inicial (variáveis de ambiente ou credencial DEMO) se ainda não existir.
     * Chamado na inicialização, depois de criar as tabelas. Nunca sobrescreve uma senha existente.
     */
    public void garantirAdministradorInicial() {
        String login = System.getenv("CLINICA_ADMIN_LOGIN");
        String senha = System.getenv("CLINICA_ADMIN_SENHA");
        if (login == null || login.isBlank() || senha == null || senha.isBlank()) {
            login = DEMO_LOGIN;
            senha = DEMO_SENHA;
        }
        login = login.strip();
        if (repository.buscarPorLogin(login) == null) {
            repository.salvar(login, Seguranca.gerarHash(senha), Usuario.ADMIN, null);
        }
    }

    /**
     * Retorna o usuário se as credenciais forem válidas e ele estiver ativo; null caso contrário.
     * Lança IllegalArgumentException se login/senha estiverem vazios ou grandes demais.
     */
    public Usuario autenticar(String login, String senha) {
        if (login == null || login.isBlank() || senha == null || senha.isBlank()) {
            throw new IllegalArgumentException("Login e senha são obrigatórios.");
        }
        if (login.length() > LOGIN_MAX || senha.length() > SENHA_MAX) {
            throw new IllegalArgumentException("Login ou senha excedem o tamanho máximo permitido.");
        }

        Usuario usuario = repository.buscarPorLogin(login.strip());
        boolean senhaConfere = Seguranca.verificar(senha, usuario != null ? usuario.getSenhaHash() : hashFalso);
        return (usuario != null && senhaConfere && usuario.isAtivo()) ? usuario : null;
    }

    // ---------------------------------------------------------------- sessões

    /** Cria uma sessão para o usuário e devolve o token (enviado ao navegador em cookie HttpOnly). */
    public synchronized String iniciarSessao(Usuario usuario) {
        long agora = System.currentTimeMillis();
        sessoes.values().removeIf(s -> agora - s.ultimoUso > INATIVIDADE_MS);
        String token = Seguranca.gerarToken();
        sessoes.put(token, new Sessao(usuario.getId(), agora));
        return token;
    }

    /**
     * Retorna o usuário da sessão (renovando o prazo de inatividade) ou null se a sessão for
     * inexistente/expirada ou se o usuário foi desativado ou excluído desde o login.
     */
    public Usuario validarSessao(String token) {
        int usuarioId = renovarSessao(token);
        if (usuarioId < 0) return null;

        Usuario usuario = repository.buscarPorId(usuarioId);
        if (usuario == null || !usuario.isAtivo()) {
            encerrarSessao(token);
            return null;
        }
        return usuario;
    }

    private synchronized int renovarSessao(String token) {
        if (token == null) return -1;
        Sessao sessao = sessoes.get(token);
        if (sessao == null) return -1;

        long agora = System.currentTimeMillis();
        if (agora - sessao.ultimoUso > INATIVIDADE_MS) {
            sessoes.remove(token);
            return -1;
        }
        sessao.ultimoUso = agora;
        return sessao.usuarioId;
    }

    public synchronized void encerrarSessao(String token) {
        if (token != null) sessoes.remove(token);
    }

    /** Derruba todas as sessões de um usuário (ao desativar, excluir ou trocar a senha). */
    public synchronized void encerrarSessoesDoUsuario(int usuarioId) {
        sessoes.values().removeIf(s -> s.usuarioId == usuarioId);
    }
}
