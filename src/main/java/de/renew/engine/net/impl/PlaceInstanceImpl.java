package de.renew.engine.net.impl;

import de.renew.engine.net.PlaceInstance;
import de.renew.engine.net.Weight;
import de.renew.engine.structure.StructureCursor;
import de.renew.engine.structure.StructureGuide;
import de.renew.engine.structure.StructureTypes;

import java.util.*;
import java.util.function.Function;

public class PlaceInstanceImpl<W extends Weight> implements PlaceInstance<W> {
    private final W zeroWeight;
    private final IdSource idSource;

    private final Map<Object, WeightedToken<W>> byToken = new HashMap<>();
    private final Map<Long, WeightedToken<W>> byId = new HashMap<>();
    private final Map<Long, Set<Long>> structureIndex = new HashMap<>();
    private final StructureGuide structureGuide;

    private static class WeightedToken<W> {
        private final Object token;
        private W weight;
        private final long id;
        private final long[] hashes;

        private WeightedToken(Object token, long id, W weight, long[] hashes) {
            this.token = token;
            this.weight = weight;
            this.id = id;
            this.hashes = hashes;
        }
    }

    public PlaceInstanceImpl(W zeroWeight, IdSource idSource, StructureTypes structureTypes) {
        this.zeroWeight = zeroWeight;
        this.idSource = idSource;
        structureGuide = new StructureGuide(structureTypes);
    }

    @Override
    public synchronized W get(Object token) {
        WeightedToken<W> weightedToken = byToken.get(token);
        return weightedToken == null ? zeroWeight : weightedToken.weight;
    }

    @Override
    public synchronized void update(Function<W, W> transformer, Object token) {
        WeightedToken<W> oldWeightedToken = byToken.get(token);
        W oldWeight = oldWeightedToken == null ? zeroWeight : oldWeightedToken.weight;
        W newWeight = transformer.apply(oldWeight);
        if (newWeight.isZero()) {
            if (oldWeightedToken != null) {
                long id = oldWeightedToken.id;
                byToken.remove(token);
                byId.remove(id);
                long[] hashes = oldWeightedToken.hashes;
                for (long longHash : hashes) {
                    Set<Long> oldIds = structureIndex.get(longHash);
                    if (oldIds == null) {
                        throw new IllegalStateException("structure index is broken");
                    }
                    oldIds.remove(id);
                    if (oldIds.isEmpty()) {
                        structureIndex.remove(longHash);
                    }
                }
            }
        } else {
            if (oldWeightedToken != null) {
                oldWeightedToken.weight = newWeight;
            } else {
                long id = idSource.newId();
                Set<Long> hashes = new HashSet<>();
                structureGuide.guide((StructureCursor cursor, Object element, long elementHash) -> {
                    long hash = cursor.getPositionHash() + elementHash;
                    hashes.add(hash);
                }, token);
                for (Long hash : hashes) {
                    Set<Long> oldIds = structureIndex.computeIfAbsent(hash, k -> new TreeSet<>());
                    oldIds.add(id);
                }
                WeightedToken<W> weightedToken = new WeightedToken<>(token, id, newWeight, hashes.stream().mapToLong(hash -> hash).toArray());
                byToken.put(token, weightedToken);
                byId.put(id, weightedToken);
            }
        }
    }

    @Override
    public synchronized List<Object> getCandidates(StructureCursor cursor, Object element, long elementHash) {
        long hash = cursor.getPositionHash() + elementHash;
        Set<Long> ids = structureIndex.getOrDefault(hash, Set.of());
        return ids.stream().map(id -> byId.get(id).token).toList();
    }
}
