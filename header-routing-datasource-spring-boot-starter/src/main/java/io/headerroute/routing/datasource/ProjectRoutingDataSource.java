package io.headerroute.routing.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class ProjectRoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(ProjectRoutingDataSource.class);

    @Override
    protected Object determineCurrentLookupKey() {
        String project = ProjectContext.get();
        log.debug("Routing datasource lookup for project={}", project);
        return project;
    }
}
