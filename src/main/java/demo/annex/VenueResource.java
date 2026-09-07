package demo.annex;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
public class VenueResource
{
    @Id
    String id;
    String name;
    String kind;
    int stock;
    int priceCents;
    LocalTime availableFrom;
    LocalTime availableUntil;
    boolean veganSupported;
    @Version
    long version;

    protected VenueResource()
    {
    }
}
