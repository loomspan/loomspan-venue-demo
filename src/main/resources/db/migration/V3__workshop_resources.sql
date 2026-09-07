ALTER TABLE event_request ADD event_type VARCHAR(20) NOT NULL DEFAULT 'MEETING';
ALTER TABLE event_request ADD standard_lunches INTEGER NOT NULL DEFAULT 0;
ALTER TABLE event_request ADD vegan_lunches INTEGER NOT NULL DEFAULT 0;
ALTER TABLE event_request ADD presentation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE event_request ADD livestream BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE proposal ADD allocation_json VARCHAR(16000);
ALTER TABLE proposal ADD breakout_room_id VARCHAR(40) REFERENCES room(id);
ALTER TABLE proposal ADD lunch_package_id VARCHAR(40);

CREATE TABLE venue_resource (
 id VARCHAR(40) PRIMARY KEY, name VARCHAR(100) NOT NULL, kind VARCHAR(40) NOT NULL,
 stock INTEGER NOT NULL CHECK(stock > 0), price_cents INTEGER NOT NULL CHECK(price_cents >= 0),
 available_from TIME NOT NULL, available_until TIME NOT NULL,
 vegan_supported BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO venue_resource(id,name,kind,stock,price_cents,available_from,available_until,vegan_supported) VALUES
 ('FOOD-BOX','Boxed lunch','LUNCH',100,1800,'12:30','13:30',TRUE),
 ('FOOD-PLUS','Enhanced boxed lunch','LUNCH',100,2400,'12:30','13:30',TRUE),
 ('FOOD-DRINK','Drinks service','DRINKS',100,600,'12:30','13:30',FALSE),
 ('EQ-PRESENT','Presentation kit','EQUIPMENT',2,20000,'12:30','18:30',FALSE),
 ('EQ-STREAM','Livestream kit','EQUIPMENT',1,30000,'12:30','18:30',FALSE),
 ('STAFF-LEE','Lee — streaming operator','STREAM_OPERATOR',1,5000,'12:30','18:30',FALSE),
 ('STAFF-SAM','Sam — catering attendant','CATERING_ATTENDANT',1,0,'12:30','13:30',FALSE);
ALTER TABLE proposal ADD CONSTRAINT proposal_lunch_fk FOREIGN KEY(lunch_package_id) REFERENCES venue_resource(id);
CREATE TABLE resource_reservation (
 id VARCHAR(40) PRIMARY KEY, booking_id VARCHAR(40) NOT NULL REFERENCES booking(id) ON DELETE CASCADE,
 resource_id VARCHAR(40) NOT NULL, quantity INTEGER NOT NULL CHECK(quantity > 0),
 starts_at TIMESTAMP NOT NULL, ends_at TIMESTAMP NOT NULL, CHECK(ends_at > starts_at),
 UNIQUE(booking_id,resource_id)
);
CREATE INDEX resource_interval ON resource_reservation(resource_id, starts_at, ends_at);
INSERT INTO resource_reservation(id,booking_id,resource_id,quantity,starts_at,ends_at)
 SELECT id,id,room_id,1,starts_at,ends_at FROM booking;
