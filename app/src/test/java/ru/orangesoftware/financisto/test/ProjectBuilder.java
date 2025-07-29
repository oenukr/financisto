package ru.orangesoftware.financisto.test;

import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.model.Project;

public class ProjectBuilder {
    private final DatabaseAdapter db;
    private final Project project = new Project();

    public static ProjectBuilder withDb(DatabaseAdapter db) {
        return new ProjectBuilder(db);
    }

    private ProjectBuilder(DatabaseAdapter db) {
        this.db = db;
        this.project.setActive(false);
    }

    public ProjectBuilder id(long v) {
        project.setId(v);
        return this;
    }

    public ProjectBuilder title(String v) {
        project.setTitle(v);
        return this;
    }

    public ProjectBuilder setActive() {
        project.setActive(true);
        return this;
    }

    public Project create() {
        db.saveOrUpdate(project);
        return project;
    }


}
