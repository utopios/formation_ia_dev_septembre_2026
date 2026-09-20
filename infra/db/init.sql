-- Initialisation de la base de la formation "IA Generative pour Developpeurs".
-- Ce script est joue automatiquement par l'image pgvector au premier demarrage
-- (repertoire /docker-entrypoint-initdb.d/). Il n'est PAS rejoue si le volume
-- de donnees existe deja : pour repartir de zero, supprimer le volume
-- (podman compose down -v).

-- Extension pgvector : indispensable pour le type "vector" et les operateurs
-- de distance (<=> cosinus, <-> L2, <#> produit scalaire).
CREATE EXTENSION IF NOT EXISTS vector;

-- Table d'exemple utilisee comme socle par les demos et TP RAG (module 5).
-- La dimension 768 correspond a la sortie du modele d'embeddings
-- nomic-embed-text (embedding_length = 768, verifie via l'API Ollama).
-- Ne PAS changer cette dimension sans changer aussi le modele d'embeddings.
CREATE TABLE IF NOT EXISTS documents (
    id        BIGSERIAL PRIMARY KEY,
    service   TEXT,                 -- service metier d'origine (RH, Finance, ...)
    contenu   TEXT NOT NULL,        -- texte source du chunk
    embedding vector(768),          -- vecteur nomic-embed-text (dimension 768)
    metadata  JSONB DEFAULT '{}'::jsonb
);

-- Index de recherche par similarite cosinus.
-- HNSW offre un bon compromis rappel/latence ; il se cree meme sur table vide.
-- vector_cosine_ops correspond a l'operateur <=> utilise dans les requetes.
CREATE INDEX IF NOT EXISTS documents_embedding_hnsw
    ON documents
    USING hnsw (embedding vector_cosine_ops);

-- Index sur le service pour filtrer un corpus par origine (controle d'acces
-- par service, illustre en module 5 - confidentialite by design).
CREATE INDEX IF NOT EXISTS documents_service_idx ON documents (service);
