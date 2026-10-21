create table outbox_event (
    id           bigserial primary key,
    event_id     uuid        not null unique,
    aggregate_id uuid        not null,
    event_type   varchar(64) not null,
    payload      jsonb       not null,
    created_at   timestamptz not null default now(),
    published_at timestamptz
);

create index outbox_event_unpublished on outbox_event (id) where published_at is null;
create index outbox_event_aggregate on outbox_event (aggregate_id);

create table processed_message (
    event_id     uuid primary key,
    event_type   varchar(64) not null,
    processed_at timestamptz not null default now()
);
