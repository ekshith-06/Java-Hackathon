public class Donor {
    private int id;
    private String donorName, bloodGroup, city, phoneNumber;
    private boolean available;

    public Donor(String donorName, String bloodGroup, String city, boolean available, String phoneNumber) {
        this(0, donorName, bloodGroup, city, available, phoneNumber);
    }

    public Donor(int id, String donorName, String bloodGroup, String city, boolean available, String phoneNumber) {
        this.id = id;
        this.donorName = donorName;
        this.bloodGroup = bloodGroup;
        this.city = city;
        this.available = available;
        this.phoneNumber = phoneNumber;
    }

    public int getId() { return id; }
    public String getDonorName() { return donorName; }
    public String getBloodGroup() { return bloodGroup; }
    public String getCity() { return city; }
    public boolean isAvailable() { return available; }
    public String getPhoneNumber() { return phoneNumber; }

    public void setId(int id) { this.id = id; }
    public void setDonorName(String donorName) { this.donorName = donorName; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public void setCity(String city) { this.city = city; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String toJson() {
        return "{\"id\":" + id
                + ",\"donorName\":" + Json.str(donorName)
                + ",\"bloodGroup\":" + Json.str(bloodGroup)
                + ",\"city\":" + Json.str(city)
                + ",\"available\":" + available
                + ",\"phoneNumber\":" + Json.str(phoneNumber) + "}";
    }
}
