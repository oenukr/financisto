package ru.orangesoftware.financisto.report;

import androidx.annotation.NonNull;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.graph.GraphStyle;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.utils.SummaryEntityEnum;

public enum ReportType implements SummaryEntityEnum {

	BY_PERIOD(R.string.report_by_period, R.string.report_by_period_summary, R.drawable.report_icon_default){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return new PeriodReport(currency, skipTransfers, screenDensity);
		}
	},
	BY_CATEGORY(R.string.report_by_category, R.string.report_by_category_summary, R.drawable.report_icon_default){
        @Override
        public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
            return new CategoryReport(currency, skipTransfers, screenDensity);
        }
	},
	BY_SUB_CATEGORY(R.string.report_by_category, R.string.report_by_category_summary, R.drawable.report_icon_default){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return new SubCategoryReport(currency, skipTransfers, screenDensity);
		}
	},
    BY_PAYEE(R.string.report_by_payee, R.string.report_by_payee_summary, R.drawable.report_icon_default){
        @Override
        public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
            return new PayeesReport(currency, skipTransfers, screenDensity);
        }
    },
	BY_LOCATION(R.string.report_by_location, R.string.report_by_location_summary, R.drawable.report_icon_default){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return new LocationsReport(currency, skipTransfers, screenDensity);
		}
	},
	BY_PROJECT(R.string.report_by_project, R.string.report_by_project_summary, R.drawable.report_icon_default){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return new ProjectsReport(currency, skipTransfers, screenDensity);
		}
	}, 
	BY_ACCOUNT_BY_PERIOD(R.string.report_by_account_by_period, R.string.report_by_account_by_period_summary, R.drawable.actionbar_action_line_chart){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return null;
		}
		
		@Override
		public boolean isConventionalBarReport() {
			return false;
		}
	}, 
	BY_CATEGORY_BY_PERIOD(R.string.report_by_category_by_period, R.string.report_by_category_by_period_summary, R.drawable.actionbar_action_line_chart){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return null;
		}
		
		@Override
		public boolean isConventionalBarReport() {
			return false;
		}
	}, 
    BY_PAYEE_BY_PERIOD(R.string.report_by_payee_by_period, R.string.report_by_payee_by_period_summary, R.drawable.actionbar_action_line_chart){
        @Override
        public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
            return null;
        }

        @Override
        public boolean isConventionalBarReport() {
            return false;
        }
    },
	BY_LOCATION_BY_PERIOD(R.string.report_by_location_by_period, R.string.report_by_location_by_period_summary, R.drawable.actionbar_action_line_chart){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return null;
		}
		
		@Override
		public boolean isConventionalBarReport() {
			return false;
		}
	}, 
	BY_PROJECT_BY_PERIOD(R.string.report_by_project_by_period, R.string.report_by_project_by_period_summary, R.drawable.actionbar_action_line_chart){
		@Override
		public Report createReport(Currency currency, boolean skipTransfers, float screenDensity) {
			return null;
		}
		
		@Override
		public boolean isConventionalBarReport() {
			return false;
		}
	};
	
	public final int titleId;
	public final int summaryId;
	public final int iconId;
	
	ReportType(int titleId, int summaryId, int iconId) {
		this.titleId = titleId;
		this.summaryId = summaryId;
		this.iconId = iconId;
	}

	@Override
	public int getTitleId() {
		return titleId;
	}

	@Override
	public int getSummaryId() {
		return summaryId;
	}

	@Override
	public int getIconId() {
		return iconId;
	}

	@NonNull
	@Override
	public String getName() {
		return name();
	}

	public boolean isConventionalBarReport() {
		return true;
	}
	
	public abstract Report createReport(Currency currency, boolean skipTransfers, float screenDensity);

}
