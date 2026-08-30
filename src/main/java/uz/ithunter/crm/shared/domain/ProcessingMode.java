package uz.ithunter.crm.shared.domain;

/**
 * Traditional vs expedited processing (spec 1.8, 12.2). Lives in {@code shared} because it is not
 * owned by any one module: {@code electronic_case}, {@code price_rule}, {@code price_calculation}
 * and {@code contract} all constrain their own column to these two values.
 *
 * <p>ASSUMPTIONS.md A3 fixes the DEMO coefficients (1.0 / 1.5); the values themselves are
 * configuration rows in {@code price_rule}, not constants in this enum.
 */
public enum ProcessingMode {
    TRADITIONAL, EXPEDITED
}
