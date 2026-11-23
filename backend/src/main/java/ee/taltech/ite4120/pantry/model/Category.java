package ee.taltech.ite4120.pantry.model;

/** Reference row from {@code pantry.category}: one of the ten seeded broad product families. */
public class Category {

    private String code;
    private String name;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
