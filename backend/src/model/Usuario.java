package model;

public class Usuario {
    public static final String ADMIN = "ADMIN";
    public static final String MEDICO = "MEDICO";

    private final int id;
    private final String login;
    private final String senhaHash;
    private final String papel;
    private final String medico;     // nome do médico na agenda (só para o perfil MEDICO)
    private final boolean ativo;

    public Usuario(int id, String login, String senhaHash, String papel, String medico, boolean ativo) {
        this.id = id;
        this.login = login;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.medico = medico;
        this.ativo = ativo;
    }

    public int getId() { return id; }
    public String getLogin() { return login; }
    public String getSenhaHash() { return senhaHash; }
    public String getPapel() { return papel; }
    public String getMedico() { return medico; }
    public boolean isAtivo() { return ativo; }

    public boolean isAdmin() { return ADMIN.equals(papel); }
    public boolean isMedico() { return MEDICO.equals(papel); }
}
