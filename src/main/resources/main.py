from sklearn_pandas import DataFrameMapper
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.preprocessing import OneHotEncoder
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.decoration import CategoricalDomain, ContinuousDomain
from sklearn2pmml.pipeline import PMMLPipeline

import pandas

audit = pandas.read_csv("csv/Audit.csv")
print(audit.head(3))

columns = audit.columns.tolist()

audit_X = audit[columns[: -1]]
audit_y = audit[columns[-1]]

audit_X = audit_X.drop(["Deductions"], axis = 1)

def sklearn_audit(classifier, name):
	pipeline = PMMLPipeline([
		("mapper", DataFrameMapper(
			[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["Employment", "Education", "Marital", "Occupation", "Gender"]] +
			[([column], ContinuousDomain()) for column in ["Age", "Income", "Hours"]]
		)),
		("classifier", classifier)
	])
	pipeline.fit(audit_X, audit_y)
	pipeline.configure(compact = False)
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml", with_repr = False)

sklearn_audit(RandomForestClassifier(n_estimators = 71, max_depth = 7, random_state = 13), "RandomForestAudit")

auto = pandas.read_csv("csv/Auto.csv")
print(auto.head(3))

columns = auto.columns.tolist()

auto_X = auto[columns[: -1]]
auto_y = auto[columns[-1]]

def sklearn_auto(regressor, name):
	pipeline = PMMLPipeline([
		("mapper", DataFrameMapper(
			[([column], [CategoricalDomain(), OneHotEncoder()]) for column in ["cylinders", "model_year", "origin"]] +
			[([column], ContinuousDomain()) for column in ["displacement", "horsepower", "weight", "acceleration"]]
		)),
		("regressor", regressor)
	])
	pipeline.fit(auto_X, auto_y)
	pipeline.configure(compact = False)
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml", with_repr = False)

sklearn_auto(RandomForestRegressor(n_estimators = 31, max_depth = 5, random_state = 13), "RandomForestAuto")