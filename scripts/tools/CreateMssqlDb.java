import java.sql.DriverManager;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Waits for a SQL Server reachable at args[0] (host:port) and creates the qtt database if it does not exist. Used by
 * stack.sh and make start_mssql, since Azure SQL Edge ships no sqlcmd.
 */
public class CreateMssqlDb {
    // Kept referenced so the setting is not garbage-collected with the logger.
    private static final Logger DRIVER_LOG = Logger.getLogger("com.microsoft.sqlserver.jdbc");

    public static void main(String[] args) throws Exception {
        DRIVER_LOG.setLevel(Level.SEVERE); // a warning per attempt while the server starts; the last error is thrown
        String url = "jdbc:sqlserver://" + args[0] + ";encrypt=true;trustServerCertificate=true";
        Exception last = null;
        for (int i = 0; i < 60; i++) {
            try (var c = DriverManager.getConnection(url, "sa", args[1]); var s = c.createStatement()) {
                s.execute("IF DB_ID('qtt') IS NULL CREATE DATABASE qtt");
                return;
            } catch (Exception e) {
                last = e;
                Thread.sleep(2000);
            }
        }
        throw last;
    }
}
