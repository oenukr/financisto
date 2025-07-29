package ru.orangesoftware.financisto.test;

import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.model.SmsTemplate;

public class SmsTemplateBuilder {

    private final DatabaseAdapter db;
    private final SmsTemplate smsTemplate = new SmsTemplate();

    public static SmsTemplate createDefault(DatabaseAdapter db) {
        return withDb(db).title("900").template("<::> <:D:> <::> <:P:>р TEREMOK<::><:B:>р").create();
    }

    public static SmsTemplateBuilder withDb(DatabaseAdapter db) {
        return new SmsTemplateBuilder(db);
    }

    private SmsTemplateBuilder(DatabaseAdapter db) {
        this.db = db;
    }

    public SmsTemplateBuilder title(String title) {
        smsTemplate.setTitle(title);
        return this;
    }

    public SmsTemplateBuilder template(String template) {
        smsTemplate.template = template;
        return this;
    }

    public SmsTemplateBuilder accountId(int id) {
        smsTemplate.accountId = id;
        return this;
    }

    public SmsTemplateBuilder categoryId(int id) {
        smsTemplate.categoryId = id;
        return this;
    }

    public SmsTemplateBuilder income(boolean income) {
        smsTemplate.isIncome = income;
        return this;
    }

    public SmsTemplateBuilder sortOrder(int sortOrder) {
        smsTemplate.sortOrder = sortOrder;
        return this;
    }

    public SmsTemplate create() {
        return db.get(SmsTemplate.class, db.saveOrUpdate(smsTemplate));
    }
}
