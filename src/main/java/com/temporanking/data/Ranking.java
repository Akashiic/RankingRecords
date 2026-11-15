package com.temporanking.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Snapshot imutavel de um ranking. Publicado via campo volatile, seguro para ler de qualquer thread. */
public final class Ranking<V> {

    public static final class Entry<V> {
        public final UUID uuid;
        public final String name;
        public final V value;

        public Entry(UUID uuid, String name, V value) {
            this.uuid = uuid;
            this.name = name;
            this.value = value;
        }
    }

    private final List<Entry<V>> entries;
    private final Map<UUID, Integer> positions;

    @SuppressWarnings("rawtypes")
    private static final Ranking EMPTY = new Ranking<Object>(Collections.<Entry<Object>>emptyList());

    @SuppressWarnings("unchecked")
    public static <V> Ranking<V> empty() {
        return (Ranking<V>) EMPTY;
    }

    /** @param sorted entradas ja ordenadas da 1a para a ultima posicao */
    public Ranking(List<Entry<V>> sorted) {
        this.entries = Collections.unmodifiableList(sorted);
        Map<UUID, Integer> pos = new HashMap<UUID, Integer>(sorted.size() * 2);
        for (int i = 0; i < sorted.size(); i++) pos.put(sorted.get(i).uuid, i + 1);
        this.positions = pos;
    }

    /** @param position 1 = primeiro lugar */
    public Entry<V> get(int position) {
        int i = position - 1;
        return i >= 0 && i < entries.size() ? entries.get(i) : null;
    }

    /** @return posicao (1 = primeiro) ou -1 se nao esta no ranking */
    public int positionOf(UUID uuid) {
        Integer p = positions.get(uuid);
        return p == null ? -1 : p;
    }

    /**
     * Posicao real se esta no ranking; senao, a posicao que teria com esse valor
     * (quantos tem valor maior + 1). Usado para mostrar a posicao a quem e ignorado.
     */
    public int positionFor(UUID uuid, V value, java.util.Comparator<V> cmp) {
        int p = positionOf(uuid);
        if (p > 0 || value == null) return p;
        int lo = 0, hi = entries.size(); // entradas em ordem decrescente: acha a 1a com valor <= value
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cmp.compare(entries.get(mid).value, value) > 0) lo = mid + 1;
            else hi = mid;
        }
        return lo + 1;
    }

    public Entry<V> entryOf(UUID uuid) {
        int p = positionOf(uuid);
        return p < 0 ? null : entries.get(p - 1);
    }

    public int size() {
        return entries.size();
    }
}
