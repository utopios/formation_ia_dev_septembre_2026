package com.novatech.orders.infrastructure;

import java.util.ArrayList;
import java.util.List;

/**
 * Point d'execution des requetes SQL.
 *
 * <p>Note d'atelier : dans l'application reelle, cette classe est un
 * {@code JdbcTemplate} Spring adosse au pool de connexions PostgreSQL. Ici
 * elle journalise simplement les requetes recues afin que le projet
 * compile et se teste sans base de donnees ni pilote JDBC. Les signatures
 * sont celles de production : requete d'un cote, parametres de l'autre.</p>
 */
public class SqlExecutor {

    private final List<String> executedStatements = new ArrayList<>();

    /**
     * Execute une requete de lecture.
     *
     * @param sql        requete SQL
     * @param parameters valeurs des parametres positionnels
     * @return les lignes retournees, vides dans cette copie de travail
     */
    public List<Object[]> query(String sql, Object... parameters) {
        executedStatements.add(sql);
        return List.of();
    }

    /**
     * Execute une requete de modification.
     *
     * @param sql        requete SQL
     * @param parameters valeurs des parametres positionnels
     * @return le nombre de lignes affectees
     */
    public int update(String sql, Object... parameters) {
        executedStatements.add(sql);
        return 1;
    }

    /** Requetes emises depuis le demarrage, utile en test. */
    public List<String> executedStatements() {
        return List.copyOf(executedStatements);
    }
}
