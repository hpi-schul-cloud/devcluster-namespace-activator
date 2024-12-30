package de.svs;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@MongoEntity(collection = "namespaces")
public class Namespace extends PanacheMongoEntityBase {
    @BsonId
    public ObjectId id = new ObjectId();
    public String name;
    public Instant activatedUntil;

    public static Namespace create(String name, Instant activatedUntil) {
        Namespace namespace = new Namespace();
        namespace.name = name;
        namespace.activatedUntil = activatedUntil;
        return namespace;
    }

    public static Optional<Namespace> findByName(String name) {
        return find("name", name).singleResultOptional();
    }

    public static List<Namespace> findByActivatedUntilOlderThan(Instant activatedUntil) {
        return find("activatedUntil < ?1", activatedUntil).list();
    }

    public static long countActiveNamespaces() {
        LocalDateTime now = LocalDateTime.now();
        return count("activatedUntil > ?1", now);
    }

    public static long countTotalNamespaces() {
        return count();
    }

    public static List<Namespace> getAll() {
        return findAll().list();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Namespace that = (Namespace) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
