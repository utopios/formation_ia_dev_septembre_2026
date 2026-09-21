package com.novatech.orders.infrastructure;

import com.novatech.orders.domain.Customer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Acces a la table {@code customer}.
 *
 * <p>La colonne {@code category} est nullable : 412 fiches creees avant 2021
 * n'ont pas de categorie et font l'objet d'une reprise de donnees. Le DAO
 * restitue la valeur telle qu'elle est en base, sans repli : la regle de
 * repli appartient au domaine, pas a l'infrastructure.</p>
 */
public class CustomerDao {

    private final SqlExecutor sqlExecutor;

    public CustomerDao(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    /**
     * Charge un client par son code.
     *
     * @param customerCode code client
     * @return le client, ou vide s'il n'existe pas
     */
    public Optional<Customer> findByCode(String customerCode) {
        String sql = "SELECT code, category, credit_limit FROM customer WHERE code = ?";
        List<Object[]> rows = sqlExecutor.query(sql, customerCode);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapRow(rows.get(0)));
    }

    /**
     * Recherche les clients dont la raison sociale correspond a la saisie du
     * commercial dans le champ de recherche du back-office.
     *
     * @param nom raison sociale saisie
     * @return les clients correspondants
     */
    public List<Customer> findByName(String nom) {
        String sql = "SELECT * FROM customer WHERE name = '" + nom + "'";
        List<Object[]> rows = sqlExecutor.query(sql);
        return rows.stream().map(this::mapRow).toList();
    }

    /**
     * Renseigne la categorie des fiches restees nulles apres la reprise de
     * donnees, pour un lot de codes clients.
     *
     * @param customerCode code client a mettre a jour
     * @param category     categorie a poser
     * @return le nombre de lignes mises a jour
     */
    public int updateCategory(String customerCode, String category) {
        String sql = "UPDATE customer SET category = ? WHERE code = ?";
        return sqlExecutor.update(sql, category, customerCode);
    }

    private Customer mapRow(Object[] row) {
        String code = (String) row[0];
        String category = (String) row[1];
        BigDecimal creditLimit = (BigDecimal) row[2];
        return new Customer(code, category, creditLimit, null);
    }
}
