import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RunSqlFile {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: RunSqlFile <jdbcUrl> <username> <password> <sqlFile>");
        }
        String jdbcUrl = args[0];
        String username = args[1];
        String password = args[2];
        Path sqlFile = Path.of(args[3]);
        String sql = Files.readString(sqlFile, StandardCharsets.UTF_8);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            for (String command : splitSql(sql)) {
                String trimmed = command.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                boolean hasResultSet = statement.execute(trimmed);
                if (hasResultSet) {
                    try (ResultSet rs = statement.getResultSet()) {
                        int columns = rs.getMetaData().getColumnCount();
                        while (rs.next()) {
                            List<String> values = new ArrayList<>();
                            for (int i = 1; i <= columns; i++) {
                                values.add(rs.getMetaData().getColumnLabel(i) + "=" + rs.getString(i));
                            }
                            System.out.println(String.join(", ", values));
                        }
                    }
                } else {
                    System.out.println("updated=" + statement.getUpdateCount());
                }
            }
        }
    }

    private static List<String> splitSql(String sql) {
        List<String> commands = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean lineComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                if (ch == '\n' || ch == '\r') {
                    lineComment = false;
                    current.append(ch);
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && ch == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            }
            if (ch == ';' && !inSingleQuote && !inDoubleQuote) {
                commands.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            commands.add(current.toString());
        }
        return commands;
    }
}
