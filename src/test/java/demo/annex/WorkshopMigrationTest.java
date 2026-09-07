package demo.annex;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class WorkshopMigrationTest {
    @Test void upgradesExistingRoomBookingsWithoutResettingThem() throws Exception {
        String url="jdbc:h2:mem:annex-upgrade;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("2").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO event_request VALUES ('old-event','Saved meeting','2026-10-16',20,50000,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO assessment VALUES ('old-assessment','old-event','BOOKED','Saved result','','session',CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO proposal VALUES ('old-proposal','old-assessment','ROOM-C',0,30000)");
            s.executeUpdate("INSERT INTO booking VALUES ('old-booking','old-proposal','ROOM-C','Saved meeting','2026-10-16 12:30:00','2026-10-16 18:30:00',30000)");
        }
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var rows=s.executeQuery("SELECT event_type FROM event_request WHERE id='old-event'")) {assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("MEETING");}
            try(var rows=s.executeQuery("SELECT COUNT(*) FROM resource_reservation WHERE booking_id='old-booking' AND resource_id='ROOM-C'")) {rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
            try(var rows=s.executeQuery("SELECT COUNT(*) FROM booking")) {rows.next();assertThat(rows.getInt(1)).isEqualTo(3);}
        }
    }
}
