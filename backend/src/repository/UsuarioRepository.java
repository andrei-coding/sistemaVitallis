package repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import model.Usuario;
import util.Database;
import util.DatabaseException;

public class UsuarioRepository {
    private static final String COLUNAS = "id, login, senha_hash, papel, medico, ativo";

    public Usuario salvar(String login, String senhaHash, String papel, String medico) {
        String sql = "INSERT INTO usuarios (login, senha_hash, papel, medico, ativo) "
                + "VALUES (?, ?, ?, ?, TRUE) RETURNING id";
        try (Connection conexao = Database.conectar();
             PreparedStatement statement = conexao.prepareStatement(sql)) {
            statement.setString(1, login);
            statement.setString(2, senhaHash);
            statement.setString(3, papel);
            statement.setString(4, medico);
            try (ResultSet resultado = statement.executeQuery()) {
                if (!resultado.next()) throw new SQLException("O PostgreSQL não retornou o ID do usuário.");
                return new Usuario(resultado.getInt("id"), login, senhaHash, papel, medico, true);
            }
        } catch (SQLException e) {
            throw erroBanco("Não foi possível salvar o usuário.", e);
        }
    }

    public Usuario buscarPorId(int id) {
        return buscar("SELECT " + COLUNAS + " FROM usuarios WHERE id = ?", id, null);
    }

    /** A busca por login ignora maiúsculas e minúsculas. */
    public Usuario buscarPorLogin(String login) {
        return buscar("SELECT " + COLUNAS + " FROM usuarios WHERE LOWER(login) = LOWER(?)", null, login);
    }

    public List<Usuario> listar() {
        List<Usuario> usuarios = new ArrayList<>();
        try (Connection conexao = Database.conectar();
             PreparedStatement statement = conexao.prepareStatement(
                     "SELECT " + COLUNAS + " FROM usuarios ORDER BY id");
             ResultSet resultado = statement.executeQuery()) {
            while (resultado.next()) usuarios.add(mapear(resultado));
            return usuarios;
        } catch (SQLException e) {
            throw erroBanco("Não foi possível listar os usuários.", e);
        }
    }

    /** Atualiza o médico vinculado e o status. Retorna null se o usuário não existir. */
    public Usuario atualizar(int id, String medico, boolean ativo) {
        try (Connection conexao = Database.conectar();
             PreparedStatement statement = conexao.prepareStatement(
                     "UPDATE usuarios SET medico = ?, ativo = ? WHERE id = ?")) {
            statement.setString(1, medico);
            statement.setBoolean(2, ativo);
            statement.setInt(3, id);
            if (statement.executeUpdate() == 0) return null;
            return buscarPorId(id);
        } catch (SQLException e) {
            throw erroBanco("Não foi possível atualizar o usuário.", e);
        }
    }

    public boolean atualizarSenha(int id, String senhaHash) {
        try (Connection conexao = Database.conectar();
             PreparedStatement statement = conexao.prepareStatement(
                     "UPDATE usuarios SET senha_hash = ? WHERE id = ?")) {
            statement.setString(1, senhaHash);
            statement.setInt(2, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw erroBanco("Não foi possível atualizar a senha.", e);
        }
    }

    public boolean remover(int id) {
        try (Connection conexao = Database.conectar();
             PreparedStatement statement = conexao.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw erroBanco("Não foi possível excluir o usuário.", e);
        }
    }

    private Usuario buscar(String sql, Integer id, String login) {
        try (Connection conexao = Database.conectar();
             PreparedStatement statement = conexao.prepareStatement(sql)) {
            if (id != null) statement.setInt(1, id);
            else statement.setString(1, login);
            try (ResultSet resultado = statement.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }
        } catch (SQLException e) {
            throw erroBanco("Não foi possível buscar o usuário.", e);
        }
    }

    private static Usuario mapear(ResultSet r) throws SQLException {
        return new Usuario(r.getInt("id"), r.getString("login"), r.getString("senha_hash"),
                r.getString("papel"), r.getString("medico"), r.getBoolean("ativo"));
    }

    private static DatabaseException erroBanco(String mensagem, SQLException e) {
        return new DatabaseException(mensagem, e.getSQLState(), e);
    }
}
