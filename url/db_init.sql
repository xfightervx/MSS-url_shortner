Drop DATABASE IF EXISTS mss_db;
CREATE DATABASE mss_db;

Drop user if exists mss_admin;
CREATE USER mss_admin WITH PASSWORD '0';

alter database mss_db owner to mss_admin;

GRANT CONNECT ON DATABASE mss_db TO mss_admin;

\c mss_db

GRANT pg_read_all_data TO mss_admin;
GRANT pg_write_all_data TO mss_admin;

drop schema if exists mss_transaction cascade;
create schema mss_transaction;

drop schema if exists mss_analytics cascade;
create schema mss_analytics;


create table mss_transaction.user (
    id serial primary key,
    username varchar(255) not null,
    password varchar(255) not null
);

create table mss_transaction.url (
    short_url varchar(255) primary key,
    user_id int not null references mss_transaction.user(id) on delete cascade,
    url varchar(255) not null
);

create table mss_analytics.raw (
    id serial primary key,
    url_id varchar(255) not null references mss_transaction.url(short_url) on delete cascade,
    hit_time timestamp not null default current_timestamp,
    ip_address varchar(255) not null
);
