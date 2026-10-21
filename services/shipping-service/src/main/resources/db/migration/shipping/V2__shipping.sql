create table carrier (
    id        bigint primary key,
    country   varchar(2)   not null,
    name      varchar(100) not null,
    lead_days int          not null check (lead_days >= 0)
);

create index carrier_country on carrier (country);

create table shipment (
    order_id      uuid primary key,
    status        varchar(16) not null,
    carrier       varchar(100),
    delivery_date date,
    reason        varchar(500),
    created_at    timestamptz not null
);

insert into carrier (id, country, name, lead_days) values
    (1, 'HR', 'Hrvatska posta', 3),
    (2, 'HR', 'GLS Croatia', 2),
    (3, 'DE', 'DHL', 2),
    (4, 'US', 'UPS', 5);
