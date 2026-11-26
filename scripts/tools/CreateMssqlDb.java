import java.sql.DriverManager;

/** Creates the qtt database on a SQL Server reachable at args[0] (host:port). Used by stack.sh where sqlcmd is absent. */
public class CreateMssqlDb {
    public static void main(String[] args) throws Exception {
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
