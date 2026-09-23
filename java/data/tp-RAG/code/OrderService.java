package com.novatech.orders.service;

import com.novatech.orders.domain.BusinessException;
import com.novatech.orders.domain.Cart;
import com.novatech.orders.domain.Discount;
import com.novatech.orders.domain.DiscountPolicy;
import com.novatech.orders.domain.Order;
import com.novatech.orders.domain.OrderStatus;
import com.novatech.orders.infrastructure.OrderDao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Orchestration du tunnel de commande : recalcul du panier, controles
 * bloquants a la validation, creation de la commande.
 *
 * <p>Aucune regle de remise n'est calculee ici : le service delegue au
 * domaine. Un calcul de taux dans cette couche serait un motif de refus en
 * revue de code.</p>
 */
public class OrderService {

    /** Montant minimum de commande, HT, apres remise. */
    private static final BigDecimal MINIMUM_ORDER_AMOUNT = new BigDecimal("150.00");

    /** Taux de TVA applicable en France metropolitaine. */
    private static final BigDecimal VAT_RATE = new BigDecimal("0.20");

    private final DiscountPolicy discountPolicy;
    private final DiscountValidationService validationService;
    private final OrderDao orderDao;

    public OrderService(DiscountPolicy discountPolicy,
                        DiscountValidationService validationService,
                        OrderDao orderDao) {
        this.discountPolicy = discountPolicy;
        this.validationService = validationService;
        this.orderDao = orderDao;
    }

    /**
     * Levee quand un controle bloquant de la validation echoue. Le panier
     * n'est jamais vide par un echec de validation : le message designe le
     * motif afin que le client corrige lui-meme.
     */
    public static class OrderValidationException extends BusinessException {
        public OrderValidationException(String code, String message) {
            super(code, message);
        }
    }

    /**
     * Recalcule le panier : somme des lignes, remise retenue, frais de port.
     *
     * <p>Un panier vide donne une remise de 0,00 EUR et ne leve aucune
     * erreur : c'est un cas limite exige par la specification, pas une
     * tolerance.</p>
     *
     * @param cart panier a recalculer
     * @return le detail du recalcul, destine a l'affichage dans le tunnel
     */
    public CartRecalculation recalculate(Cart cart) {
        BigDecimal amountExclVat = cart.totalExclVat();
        if (cart.isEmpty()) {
            return new CartRecalculation(amountExclVat, Discount.none(),
                    amountExclVat, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    amountExclVat);
        }

        Discount discount = discountPolicy.computeBestDiscount(cart.getCustomer(), amountExclVat);
        BigDecimal amountAfterDiscount = amountExclVat.subtract(discount.amount());
        BigDecimal shipping = cart.shippingCost(amountAfterDiscount);
        BigDecimal totalInclVat = computeTotalInclVat(amountAfterDiscount.add(shipping));

        return new CartRecalculation(amountExclVat, discount, amountAfterDiscount,
                shipping, totalInclVat);
    }

    /**
     * Applique la TVA sur le total HT remise, frais de port inclus,
     * conformement a l'ordre de calcul impose par la specification.
     *
     * @param totalExclVat total HT apres remise, port inclus
     * @return le total TTC arrondi au centime
     */
    private BigDecimal computeTotalInclVat(BigDecimal totalExclVat) {
        return totalExclVat.multiply(BigDecimal.ONE.add(VAT_RATE))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Valide le panier et cree la commande.
     *
     * <p>Si la remise appliquee depasse le seuil de delegation, la commande
     * part en validation hierarchique et n'est pas transmise a la
     * facturation avant accord.</p>
     *
     * @param cart        panier a valider
     * @param orderNumber numero de commande, aussi utilise comme cle
     *                    d'idempotence a l'appel de Sage
     * @return la commande creee
     * @throws OrderValidationException si un controle bloquant echoue
     */
    public Order placeOrder(Cart cart, String orderNumber) {
        if (cart.isEmpty()) {
            throw new OrderValidationException("CART_EMPTY",
                    "Le panier ne contient aucune ligne");
        }

        CartRecalculation recalculation = recalculate(cart);

        if (recalculation.amountAfterDiscount().compareTo(MINIMUM_ORDER_AMOUNT) < 0) {
            throw new OrderValidationException("MINIMUM_ORDER_AMOUNT",
                    "Montant minimum de commande : 150 EUR HT");
        }

        BigDecimal creditLimit = cart.getCustomer().getCreditLimit();
        if (recalculation.amountAfterDiscount().compareTo(creditLimit) > 0) {
            throw new OrderValidationException("CREDIT_LIMIT_EXCEEDED",
                    "Encours superieur au plafond de credit du client");
        }

        Discount discount = recalculation.discount();
        OrderStatus status = validationService.requiresHumanApproval(discount.rate())
                ? OrderStatus.PENDING_APPROVAL
                : OrderStatus.PENDING_INVOICING;

        Order order = new Order(orderNumber, cart.getCustomer(),
                recalculation.amountExclVat(), discount, status, Instant.now());
        orderDao.save(order);
        return order;
    }

    /**
     * Applique un geste commercial hors bareme sur une commande existante.
     *
     * <p>Le motif est obligatoire et fait vingt caracteres minimum : le
     * controle de gestion recoit un etat mensuel de ces gestes et un motif
     * vide rend l'etat inexploitable.</p>
     *
     * @param order  commande concernee
     * @param rate   taux accorde, en pourcentage
     * @param reason motif commercial documente
     */
    public void applyCommercialGesture(Order order, BigDecimal rate, String reason) {
        if (reason == null || reason.trim().length() < 20) {
            throw new OrderValidationException("GESTURE_REASON_TOO_SHORT",
                    "Le motif du geste commercial fait 20 caracteres minimum");
        }
        discountPolicy.checkRateWithinCap(rate);
        validationService.requiredApproval(rate);

        BigDecimal baseAmount = order.getAmountExclVatBeforeDiscount();
        double gestureAmount = baseAmount.doubleValue() * rate.doubleValue() / 100;
        BigDecimal amount = new BigDecimal(gestureAmount).setScale(2, RoundingMode.HALF_UP);

        orderDao.saveDiscountOverride(order.getOrderNumber(), rate, reason);
        orderDao.saveGestureAmount(order.getOrderNumber(), amount);
    }

    /**
     * Detail d'un recalcul de panier, tel qu'affiche dans le tunnel.
     *
     * @param amountExclVat       montant HT des articles avant remise
     * @param discount            remise retenue
     * @param amountAfterDiscount montant HT des articles apres remise
     * @param shippingCost        frais de port HT
     * @param totalInclVat        total TTC
     */
    public record CartRecalculation(BigDecimal amountExclVat,
                                    Discount discount,
                                    BigDecimal amountAfterDiscount,
                                    BigDecimal shippingCost,
                                    BigDecimal totalInclVat) {
    }
}
