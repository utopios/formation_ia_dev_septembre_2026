package com.novatech.orders.web;

/**
 * Corps d'erreur au format {@code application/problem+json} (RFC 7807).
 *
 * <p>Le champ {@code code} est le code stable porte par l'exception metier :
 * c'est lui que le front consomme pour choisir le message affiche, pas le
 * libelle, qui peut evoluer sans preavis.</p>
 *
 * @param status statut HTTP
 * @param title  libelle court du probleme
 * @param code   code metier stable
 * @param detail message destine au diagnostic
 */
public record ProblemDetail(int status, String title, String code, String detail) {
}
