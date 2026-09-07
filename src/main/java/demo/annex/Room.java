package demo.annex;
import jakarta.persistence.*;
@Entity
public class Room {
    @Id String id;
    String name;
    int capacity;
    int priceCents;
    @Version long version;
    protected Room() {}
}
