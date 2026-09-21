package com.novatech.orders.infrastructure;

import com.novatech.orders.domain.Order;
import java.math.BigDecimal;

/**
 * Acces aux tables {@code orders}, {@code order_line} et
 * {@code order_discount}.
 *
 * <p>La table des commandes est au pluriel, par exception a la convention de
 * nommage : {@code order} est un mot reserve SQL. L'exception est assumee et
 * documentee dans la page d'architecture.</p>
 */
public class OrderDao {

    private final SqlExecutor sqlExecutor;

    public OrderDao(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    /**
     * Enregistre une commande et la remise qui lui est associee.
     *
     * @param order commande a enregistrer
     */
    public void save(Order order) {
        String insertOrder = "INSERT INTO orders "
                + "(order_number, customer_code, amount_excl_vat, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        sqlExecutor.update(insertOrder,
                order.getOrderNumber(),
                order.getCustomer().getCustomerCode(),
                order.getAmountExclVatBeforeDiscount(),
                order.getStatus().name(),
                order.getCreatedAt());

        String insertDiscount = "INSERT INTO order_discount "
                + "(order_number, rate, amount) VALUES (?, ?, ?)";
        sqlExecutor.update(insertDiscount,
                order.getOrderNumber(),
                order.getDiscount().rate(),
                order.getDiscount().amount());
    }

    /**
     * Enregistre un geste commercial : il remplace la remise calculee et
     * porte son motif, exige par le controle de gestion.
     *
     * @param orderNumber numero de commande
     * @param rate        taux accorde
     * @param reason      motif commercial documente
     */
    public void saveDiscountOverride(String orderNumber, BigDecimal rate, String reason) {
        String sql = "UPDATE order_discount SET rate = ?, reason = ? WHERE order_number = ?";
        sqlExecutor.update(sql, rate, reason, orderNumber);
    }

    /**
     * Enregistre le montant en euros correspondant au geste commercial.
     *
     * <p>Le montant est stocke en plus du taux parce que l'etat mensuel du
     * controle de gestion raisonne en euros de marge cedee, pas en points de
     * pourcentage.</p>
     *
     * @param orderNumber numero de commande
     * @param amount      montant de la remise accordee
     */
    public void saveGestureAmount(String orderNumber, BigDecimal amount) {
        String sql = "UPDATE order_discount SET amount = ? WHERE order_number = ?";
        sqlExecutor.update(sql, amount, orderNumber);
    }

    /**
     * Compte les commandes d'un client sur une periode, utilise par le
     * controle d'encours.
     *
     * @param customerCode code client
     * @return le nombre de commandes non annulees
     */
    public int countActiveOrders(String customerCode) {
        String sql = "SELECT count(*) FROM orders WHERE customer_code = ? "
                + "AND status <> 'CANCELLED'";
        return sqlExecutor.query(sql, customerCode).size();
    }
}
