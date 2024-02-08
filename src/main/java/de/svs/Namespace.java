package de.svs;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@MongoEntity(collection = "namespaces")
public class Namespace extends PanacheMongoEntityBase {
    @BsonId
    public ObjectId id = new ObjectId();
    public String name;
    public Instant activatedUntil;

    public static Optional<Namespace> findByName(String name) {
        return find("name", name).singleResultOptional();
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
