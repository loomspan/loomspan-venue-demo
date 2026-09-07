INSERT INTO room(id,name,capacity,price_cents,version) VALUES
 ('ROOM-A','Alder Hall',120,90000,0),('ROOM-B','Birch Room',60,60000,0),('ROOM-C','Cedar Room',40,30000,0);
INSERT INTO booking(id,proposal_id,room_id,title,starts_at,ends_at,total_cents) VALUES
 ('SEED-CEDAR',NULL,'ROOM-C','Morning team meeting','2026-10-15 09:00:00','2026-10-15 12:30:00',30000),
 ('SEED-ALDER',NULL,'ROOM-A','Evening reception','2026-10-15 18:30:00','2026-10-15 21:30:00',90000);
