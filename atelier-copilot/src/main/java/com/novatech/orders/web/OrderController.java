package com.novatech.orders.web;

import com.novatech.orders.domain.BusinessException;
import com.novatech.orders.domain.Cart;
import com.novatech.orders.domain.Order;
import com.novatech.orders.service.OrderService;

/**
 * Controleur REST du tunnel de commande, expose sous {@code /api/v1}.
 *
 * <p>Note d'atelier : les annotations Spring MVC sont volontairement omises
 * pour que le projet compile sans l'ecosysteme Spring. Les methodes
 * correspondent aux routes reelles, indiquees dans leur javadoc, et la
 * reponse est materialisee par {@link ApiResponse} plutot que par
 * {@code ResponseEntity}. Le controleur ne contient aucune regle metier :
 * un calcul de remise ici serait un motif de refus en revue.</p>
 */
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * {@code POST /api/v1/carts/{id}/recalculate} — recalcule un panier et
     * renvoie le detail de la remise appliquee.
     *
     * @param cart panier charge par la couche service
     * @return 200 avec le detail du recalcul, ou 422 si une regle metier est
     *         violee
     */
    public ApiResponse<CartResponse> recalculate(Cart cart) {
        try {
            OrderService.CartRecalculation recalculation = orderService.recalculate(cart);
            CartResponse body = new CartResponse(
                    recalculation.amountExclVat(),
                    recalculation.discount().rate(),
                    recalculation.discount().amount(),
                    recalculation.discount().origin().name(),
                    recalculation.amountAfterDiscount(),
                    recalculation.shippingCost(),
                    recalculation.totalInclVat());
            return ApiResponse.ok(body);
        } catch (BusinessException e) {
            return ApiResponse.problem(422, "Regle metier violee", e);
        }
    }

    /**
     * {@code POST /api/v1/orders} — valide un panier et cree la commande.
     *
     * @param cart        panier a valider
     * @param orderNumber numero de commande attribue en amont
     * @return 201 avec le numero de commande, ou 422 si un controle bloquant
     *         echoue
     */
    public ApiResponse<String> placeOrder(Cart cart, String orderNumber) {
        try {
            Order order = orderService.placeOrder(cart, orderNumber);
            return ApiResponse.created(order.getOrderNumber(),
                    "/api/v1/orders/" + order.getOrderNumber());
        } catch (BusinessException e) {
            return ApiResponse.problem(422, "Regle metier violee", e);
        }
    }

    /**
     * Reponse HTTP minimale : statut, en-tete {@code Location} eventuel, et
     * corps utile ou corps d'erreur RFC 7807.
     *
     * @param <T> type du corps utile
     */
    public record ApiResponse<T>(int status, T body, String location, ProblemDetail problem) {

        static <T> ApiResponse<T> ok(T body) {
            return new ApiResponse<>(200, body, null, null);
        }

        static <T> ApiResponse<T> created(T body, String location) {
            return new ApiResponse<>(201, body, location, null);
        }

        static <T> ApiResponse<T> problem(int status, String title, BusinessException e) {
            return new ApiResponse<>(status, null, null,
                    new ProblemDetail(status, title, e.getCode(), e.getMessage()));
        }
    }
}
