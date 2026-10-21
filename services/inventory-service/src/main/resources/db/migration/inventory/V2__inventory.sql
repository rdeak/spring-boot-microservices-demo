create table stock (
    sku       varchar(64) primary key,
    available int    not null check (available >= 0),
    reserved  int    not null check (reserved >= 0),
    version   bigint not null
);

create table reservation (
    order_id   uuid primary key,
    status     varchar(16) not null,
    reason     varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table reservation_item (
    order_id uuid        not null references reservation (order_id),
    sku      varchar(64) not null,
    quantity int         not null check (quantity > 0),
    primary key (order_id, sku)
);

insert into stock (sku, available, reserved, version) values
    ('SKU-1', 100, 0, 0),
    ('SKU-2', 50, 0, 0),
    ('SKU-LOW', 1, 0, 0);
