package io.headerroute.routing.datasource;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.routing.datasource")
public class ProjectRoutingProperties {

    private String headerName = "X-Project";
    private boolean strict = true;
    private Map<String, ProjectDataSourceProperties> projects = new LinkedHashMap<>();

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public Map<String, ProjectDataSourceProperties> getProjects() {
        return projects;
    }

    public void setProjects(Map<String, ProjectDataSourceProperties> projects) {
        this.projects = projects;
    }

    public static class ProjectDataSourceProperties {
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }
}
