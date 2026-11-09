import java.io.FileInputStream;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("Connecting to MySQL ...");
            Directory directory = new Directory("db.properties");

            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream("db.properties")) { p.load(in); }
            int preferred = Integer.parseInt(p.getProperty("port", "8080").trim());

            WebServer server = new WebServer(directory);
            int port = server.start(preferred);
            String url = "http://localhost:" + port;
            System.out.println("Blood Donor Directory is running at " + url);
            System.out.println("Press Ctrl+C to stop.");
            openInChrome(url);
        } catch (ClassNotFoundException e) {
            System.err.println("\nMySQL driver not found. Put mysql-connector-j-*.jar in the 'lib' folder (run.bat / run.sh does this automatically).");
            System.exit(1);
        } catch (java.sql.SQLException e) {
            System.err.println("\nCould not connect to MySQL: " + e.getMessage());
            System.err.println("Check that MySQL is running and that user/password in db.properties are correct.");
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void openInChrome(String url) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "chrome", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "-a", "Google Chrome", url).start();
            } else {
                new ProcessBuilder("google-chrome", url).start();
            }
        } catch (Exception e) {
            try {   // Chrome not found: use the default browser
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                else System.out.println("Open this link in Chrome: " + url);
            } catch (Exception ex) {
                System.out.println("Open this link in Chrome: " + url);
            }
        }
    }
}
