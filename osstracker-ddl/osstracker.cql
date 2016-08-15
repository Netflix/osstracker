DROP KEYSPACE IF EXISTS osstracker;

CREATE KEYSPACE osstracker WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 3
};

USE osstracker;

CREATE TABLE repo_orgs (
  org_short text PRIMARY KEY,
  org_description text
);

CREATE TABLE repo_info (
  gh_repo_name text PRIMARY KEY,
  mgr_lead_empid text,
  gh_exists boolean,
  gh_org text,
  dev_lead_empid text,
  org_short text,
  gh_public boolean,
  osslifecycle text,
  db_last_stats_update timestamp,
  es_last_stats_update timestamp
);
