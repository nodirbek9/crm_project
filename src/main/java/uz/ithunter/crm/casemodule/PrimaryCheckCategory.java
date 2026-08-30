package uz.ithunter.crm.casemodule;

/**
 * Mirrors {@code ck_case_category} / {@code ck_pc_category} in V5 (spec 1.5).
 *
 * <p>The specification names the three categories but defines no criteria - ASSUMPTIONS.md A1 fixes
 * the DEMO rules that {@code PrimaryCheckEvaluator} applies. Kept strictly independent of
 * {@link PrimaryCheckDecision}: spec 1.5 and 4.6 are two separate facts about one check (test U-04).
 */
public enum PrimaryCheckCategory {
    RED, YELLOW, GREEN
}
