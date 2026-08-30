-- V12: close a genuine race in the audit hash chain (Phase 12, discovered by C-01/C-02/C-06's
-- genuinely concurrent MockMvc requests - no earlier phase ever exercised two truly simultaneous
-- audit_log INSERTs, so this never surfaced before).
--
-- audit_log_chain() (V10) reads "the last row" via a plain, unlocked
-- `SELECT row_hash ... ORDER BY seq DESC LIMIT 1`. Under READ COMMITTED, two transactions
-- inserting at the same instant can both read the SAME predecessor row before either commits, so
-- both compute prev_hash/row_hash against it. Both inserts still succeed (there is no uniqueness
-- constraint to stop them) and the row that ends up second by `seq` was hashed against a
-- predecessor that is no longer its immediate one - verify_audit_chain() then reports that row as
-- broken, even though nothing was ever tampered with. A false "chain broken" report on ordinary
-- concurrent use undermines the one thing this table exists to guarantee.
--
-- Fix: serialize chain-tip computation with a transaction-scoped advisory lock keyed to this
-- table, acquired before reading "the last row". The lock is released automatically at COMMIT, so
-- a second concurrent inserter simply waits for the first to finish (and therefore sees its row as
-- the new predecessor) instead of racing it.
CREATE OR REPLACE FUNCTION audit_log_chain() RETURNS trigger AS $$
DECLARE
    last_hash char(64);
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('audit_log_chain'));
    SELECT row_hash INTO last_hash FROM audit_log ORDER BY seq DESC LIMIT 1;
    NEW.prev_hash := last_hash;
    NEW.row_hash := encode(digest(
        coalesce(last_hash, '') || '|' ||
        NEW.id::text || '|' || coalesce(NEW.case_id::text,'') || '|' ||
        coalesce(NEW.user_id::text,'') || '|' || NEW.action || '|' ||
        NEW.entity_type || '|' || coalesce(NEW.entity_id::text,'') || '|' ||
        coalesce(NEW.old_value::text,'') || '|' || coalesce(NEW.new_value::text,'') || '|' ||
        NEW.created_at::text, 'sha256'), 'hex');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
