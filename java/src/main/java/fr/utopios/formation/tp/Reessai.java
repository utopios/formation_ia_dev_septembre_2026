package fr.utopios.formation.tp;

import java.util.function.Supplier;

/**
 * Petit utilitaire de reessai pour les appels au modele local.
 *
 * <p>Pourquoi il existe : Ollama sert une requete a la fois par modele charge.
 * Quand plusieurs processus tapent dessus en meme temps (et c'est le cas en
 * salle, avec une dizaine de postes ou un serveur mutualise), un appel peut
 * expirer alors que le service est parfaitement sain. Faire echouer un TP
 * entier sur cet aleas n'apprend rien a personne.</p>
 *
 * <p>C'est aussi, en miniature, une lecon d'industrialisation : un appel de LLM
 * est un appel reseau vers un service partage. Il faut le traiter comme tel,
 * avec reessai et backoff, exactement comme n'importe quel appel HTTP sortant.</p>
 */
public final class Reessai {

    private static final int TENTATIVES_MAX = 6;

    private Reessai() {
    }

    /**
     * Execute l'appel, en reessayant apres un delai croissant en cas d'echec.
     *
     * @param libelle nom de l'appel, affiche en cas de reessai
     * @param appel   l'appel au modele
     * @return le resultat du premier appel qui aboutit
     */
    public static <T> T avecReessai(String libelle, Supplier<T> appel) {
        RuntimeException derniere = null;
        for (int tentative = 1; tentative <= TENTATIVES_MAX; tentative++) {
            try {
                return appel.get();
            } catch (RuntimeException e) {
                derniere = e;
                // Distinction essentielle : une file saturee se debloque, un
                // modele qui ne sait pas s'arreter sur ce prompt ne se
                // debloquera JAMAIS. Rejouer six fois une expiration coute
                // une demi-heure pour rien. On remonte donc immediatement,
                // a charge de l'appelant d'escalader vers un autre modele.
                if (estExpiration(e)) {
                    System.err.printf("[reessai] %s : expiration -- le modele ne termine "
                            + "pas sur ce prompt, inutile de rejouer%n", libelle);
                    throw e;
                }
                if (tentative < TENTATIVES_MAX) {
                    // Backoff exponentiel plafonne : 2, 4, 8, 16, 30 secondes.
                    // On laisse le temps au modele de liberer la file plutot que
                    // de la saturer davantage, et le plafond evite d'attendre
                    // plusieurs minutes sur la derniere tentative.
                    long attente = Math.min(2000L * (1L << (tentative - 1)), 30_000L);
                    System.err.printf("[reessai] %s : echec %d/%d (%s), nouvelle tentative "
                                    + "dans %d ms%n",
                            libelle, tentative, TENTATIVES_MAX, e.getMessage(), attente);
                    try {
                        Thread.sleep(attente);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw new IllegalStateException(
                "Appel '" + libelle + "' en echec apres " + TENTATIVES_MAX + " tentatives",
                derniere);
    }

    /**
     * Vrai si l'echec est une EXPIRATION du delai, et non un service indisponible.
     *
     * <p>Les deux se presentent comme une exception, mais appellent des
     * reactions opposees : on rejoue une indisponibilite passagere, on
     * n'insiste pas sur une generation qui ne se termine pas.</p>
     */
    private static boolean estExpiration(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
