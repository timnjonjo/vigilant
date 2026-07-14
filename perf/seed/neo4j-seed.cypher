// Synthetic load-test graph for the 200+ TPS audit (loob-bank tenant).
// ~228k accounts, 208k REFERRED edges across 4 campaigns, 200 dense fraud rings.
// Load with the password supplied from the ignored local environment.
//
// NOTE: uses the :ReferralCode-node model (post-audit). All synthetic nodes are
// prefixed (ref-/ree-) and codes are LOOB-<n>, so cleanup.sh can remove them
// without touching demo data.

// Referrers: one indexed :ReferralCode per referrer, ISSUED_TO the account.
UNWIND range(0,19999) AS i
CALL { WITH i
  CREATE (r:Account {
    tenantId:'loob-bank', userId:'ref-'+toString(i),
    deviceId:'devr-'+toString(i),
    ipSubnet:'10.'+toString(i%256)+'.'+toString((i/256)%256)+'.0',
    ipType: CASE WHEN i%50=0 THEN 'DATACENTER' ELSE 'RESIDENTIAL' END,
    createdAt: timestamp() - i*1000, converted:false })
  MERGE (c:ReferralCode {tenantId:'loob-bank', code:'LOOB-'+toString(i)})
  SET c.campaignId = ['camp-a','camp-b','camp-c','camp-d'][i%4]
  MERGE (c)-[:ISSUED_TO]->(r)
} IN TRANSACTIONS OF 2000 ROWS;

// Referees + campaign-scoped REFERRED edges. Referrers <200 are dense rings
// (fan-out 150, shared device/subnet); the rest have fan-out 9.
UNWIND range(0,19999) AS i
CALL { WITH i
  MATCH (r:Account {tenantId:'loob-bank', userId:'ref-'+toString(i)})
  WITH r, i, ['camp-a','camp-b','camp-c','camp-d'][i%4] AS camp,
       CASE WHEN i<200 THEN 150 ELSE 9 END AS fanout
  UNWIND range(0, fanout-1) AS j
  CREATE (e:Account {
    tenantId:'loob-bank', userId:'ree-'+toString(i)+'-'+toString(j),
    deviceId: CASE WHEN i<200 THEN 'devring-'+toString(i) ELSE 'deve-'+toString(i)+'-'+toString(j) END,
    ipSubnet: CASE WHEN i<200 THEN 'subring-'+toString(i) ELSE '172.'+toString(i%256)+'.'+toString(j%256)+'.0' END,
    ipType: CASE WHEN (i+j)%50=0 THEN 'DATACENTER' ELSE 'RESIDENTIAL' END,
    createdAt: timestamp() - j*1000, converted: (j%3=0) })
  CREATE (r)-[:REFERRED {campaignId:camp, createdAt: timestamp() - j*1000, converted:(j%3=0)}]->(e)
} IN TRANSACTIONS OF 100 ROWS;

// SHARES_DEVICE / SHARES_IP_SUBNET cycles within each ring (device/IP collisions).
UNWIND range(0,199) AS i
CALL { WITH i
  UNWIND range(0,149) AS j
  MATCH (a:Account {tenantId:'loob-bank', userId:'ree-'+toString(i)+'-'+toString(j)})
  MATCH (b:Account {tenantId:'loob-bank', userId:'ree-'+toString(i)+'-'+toString((j+1)%150)})
  MERGE (a)-[:SHARES_DEVICE]-(b)
  MERGE (a)-[:SHARES_IP_SUBNET]-(b)
} IN TRANSACTIONS OF 10 ROWS;
