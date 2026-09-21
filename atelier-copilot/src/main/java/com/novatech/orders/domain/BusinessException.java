package com.novatech.orders.domain;

/**
 * Racine des exceptions metier.
 *
 * <p>Chaque exception porte un code stable, consomme par le front pour
 * choisir le message affiche a l'utilisateur. Ce code fait partie du contrat
 * d'API : il ne change pas sans evolution du contrat OpenAPI.</p>
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
