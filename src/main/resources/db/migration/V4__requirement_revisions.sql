ALTER TABLE event_request ADD series_id VARCHAR(40);
UPDATE event_request SET series_id=id;
ALTER TABLE event_request ALTER COLUMN series_id SET NOT NULL;
ALTER TABLE event_request ADD revision_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE event_request ADD CONSTRAINT event_revision_unique UNIQUE(series_id, revision_number);
