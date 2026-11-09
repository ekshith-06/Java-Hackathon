import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/** All donor data lives in MySQL (database "Donors", table "blood_donor"). */
public class Directory {
    public static final List<String> BLOOD_GROUPS =
            Arrays.asList("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");

    private final String url, user, pass, table;
    // actual column names, in the order: id, name, blood group, city, available, phone
    private final String[] col = new String[6];

    public Directory(String propsFile) throws IOException, ClassNotFoundException, SQLException {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(propsFile)) { p.load(in); }
        url = p.getProperty("url");
        user = p.getProperty("user", "root");
        pass = p.getProperty("password", "root");
        table = p.getProperty("table", "blood_donor");
        Class.forName(p.getProperty("driver", "com.mysql.cj.jdbc.Driver"));
        init();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    private void init() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS `" + table + "` ("
                    + "id INT PRIMARY KEY, donor_name VARCHAR(100) NOT NULL, blood_group VARCHAR(5) NOT NULL, "
                    + "city VARCHAR(100) NOT NULL, available BOOLEAN NOT NULL, phone_number VARCHAR(20) NOT NULL)");
            // works with an already existing table too: read column names by position
            try (ResultSet rs = st.executeQuery("SELECT * FROM `" + table + "` LIMIT 0")) {
                ResultSetMetaData md = rs.getMetaData();
                if (md.getColumnCount() < 6)
                    throw new SQLException("Table " + table + " must have 6 columns: id, name, blood group, city, available, phone");
                for (int i = 0; i < 6; i++) col[i] = md.getColumnLabel(i + 1);
            }
        }
        if (total() == 0) {   // first run: add sample donors
            add(new Donor("Raja", "AB-", "Vijayawada", false, "8008250996"));
            add(new Donor("Johnny", "AB+", "Gudivada", true, "9000084068"));
            add(new Donor("Ramesh", "O+", "Eluru", true, "6304539517"));
            add(new Donor("Suresh", "O-", "Rajahmundry", false, "6300828092"));
        }
    }

    private Donor map(ResultSet rs) throws SQLException {
        return new Donor(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getBoolean(5), rs.getString(6));
    }

    private String q(int i) { return "`" + col[i] + "`"; }

    /** Validates and cleans the donor fields; throws IllegalArgumentException with a friendly message. */
    public static void validate(Donor d) {
        d.setDonorName(clean(d.getDonorName()));
        d.setCity(clean(d.getCity()));
        d.setBloodGroup(clean(d.getBloodGroup()).toUpperCase());
        d.setPhoneNumber(clean(d.getPhoneNumber()));
        if (d.getDonorName().isEmpty()) throw new IllegalArgumentException("Name is required");
        if (d.getDonorName().length() > 100) throw new IllegalArgumentException("Name is too long");
        if (!BLOOD_GROUPS.contains(d.getBloodGroup())) throw new IllegalArgumentException("Choose a valid blood group");
        if (d.getCity().isEmpty()) throw new IllegalArgumentException("City is required");
        if (d.getCity().length() > 100) throw new IllegalArgumentException("City is too long");
        if (!d.getPhoneNumber().matches("[0-9+\\- ]{7,15}"))
            throw new IllegalArgumentException("Enter a valid phone number (7-15 digits)");
    }

    private static String clean(String s) { return s == null ? "" : s.trim().replaceAll("\\s+", " "); }

    public synchronized Donor add(Donor d) throws SQLException {
        validate(d);
        try (Connection c = connect()) {
            int id = 1;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(" + q(0) + "),0)+1 FROM `" + table + "`")) {
                if (rs.next()) id = rs.getInt(1);
            }
            String sql = "INSERT INTO `" + table + "` (" + q(0) + "," + q(1) + "," + q(2) + "," + q(3) + ","
                    + q(4) + "," + q(5) + ") VALUES (?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.setString(2, d.getDonorName());
                ps.setString(3, d.getBloodGroup());
                ps.setString(4, d.getCity());
                ps.setBoolean(5, d.isAvailable());
                ps.setString(6, d.getPhoneNumber());
                ps.executeUpdate();
            }
            d.setId(id);
            return d;
        }
    }

    public synchronized boolean update(Donor d) throws SQLException {
        validate(d);
        String sql = "UPDATE `" + table + "` SET " + q(1) + "=?," + q(2) + "=?," + q(3) + "=?," + q(4) + "=?,"
                + q(5) + "=? WHERE " + q(0) + "=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d.getDonorName());
            ps.setString(2, d.getBloodGroup());
            ps.setString(3, d.getCity());
            ps.setBoolean(4, d.isAvailable());
            ps.setString(5, d.getPhoneNumber());
            ps.setInt(6, d.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized boolean setAvailability(int id, boolean available) throws SQLException {
        String sql = "UPDATE `" + table + "` SET " + q(4) + "=? WHERE " + q(0) + "=?";
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, available);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized boolean delete(int id) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM `" + table + "` WHERE " + q(0) + "=?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Search the directory. Unavailable donors stay in the directory, but are
     * excluded when availableOnly is true (a search "specifically requesting available donors").
     */
    public synchronized List<Donor> search(String text, String bloodGroup, String city, boolean availableOnly)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM `" + table + "` WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (bloodGroup != null && !bloodGroup.isBlank()) {
            sql.append(" AND UPPER(").append(q(2)).append(")=?");
            args.add(bloodGroup.trim().toUpperCase());
        }
        if (city != null && !city.isBlank()) {
            sql.append(" AND LOWER(").append(q(3)).append(") LIKE ?");
            args.add("%" + city.trim().toLowerCase() + "%");
        }
        if (text != null && !text.isBlank()) {
            String like = "%" + text.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(").append(q(1)).append(") LIKE ? OR LOWER(").append(q(3)).append(") LIKE ? OR LOWER(")
               .append(q(2)).append(") LIKE ? OR ").append(q(5)).append(" LIKE ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        if (availableOnly) sql.append(" AND ").append(q(4)).append("=TRUE");
        sql.append(" ORDER BY ").append(q(0));
        List<Donor> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /** Number of AVAILABLE donors per blood group (all 8 groups, zero if none). */
    public synchronized Map<String, Integer> availableCounts() throws SQLException {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String g : BLOOD_GROUPS) m.put(g, 0);
        String sql = "SELECT UPPER(" + q(2) + "), COUNT(*) FROM `" + table + "` WHERE " + q(4)
                + "=TRUE GROUP BY UPPER(" + q(2) + ")";
        try (Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) m.put(rs.getString(1), rs.getInt(2));
        }
        return m;
    }

    public synchronized int total() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
