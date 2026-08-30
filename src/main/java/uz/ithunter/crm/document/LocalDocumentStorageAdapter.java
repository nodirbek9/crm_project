package uz.ithunter.crm.document;

import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LocalDocumentStorageAdapter implements DocumentStoragePort {
    
    // MVP: assume all refs are valid if they are not null,
    // or optionally we could check a local tmp path. We'll just return true to unblock the API
    // since file upload is not in scope for Phase 10 spec.
    @Override
    public boolean exists(String contentRef) {
        return contentRef != null && !contentRef.trim().isEmpty();
    }
}
