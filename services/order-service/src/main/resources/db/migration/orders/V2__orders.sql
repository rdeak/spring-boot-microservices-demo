create table orders (
    id               uuid primary key,
    status           varchar(16) not null,
    country          varchar(2)  not null,
    failure_reason   varchar(500),
    alerted_stuck_at timestamptz,
    version          bigint      not null,
    created_at       timestamptz not null,
    updated_at       timestamptz not null
);

create index orders_pending_created on orders (created_at) where status = 'PENDING';

create table order_item (
    order_id uuid        not null references orders (id),
    line_no  int         not null,
    sku      varchar(64) not null,
    quantity int         not null check (quantity > 0),
    primary key (order_id, line_no)
);
