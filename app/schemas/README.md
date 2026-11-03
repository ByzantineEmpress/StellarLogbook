# Room schema exports

Room writes the generated schema JSON (`1.json` for database version 1) into this
directory on the first build (KSP arg `room.schemaLocation`). Commit them from
v2 onward so migration tests can validate upgrades.
