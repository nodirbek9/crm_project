package uz.ithunter.crm.document;

public interface DocumentStoragePort {
    /**
     * Verifies if the referenced content exists in storage.
     * In this MVP phase, it checks the local filesystem adapter logic.
     */
    boolean exists(String contentRef);
}
