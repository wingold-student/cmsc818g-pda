import sys
import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import sklearn
#import seaborn as sns
import pandas as pd
from matplotlib import pyplot as plt
from sklearn import preprocessing
from sklearn.naive_bayes import GaussianNB
from sklearn.neighbors import KNeighborsClassifier, KNeighborsRegressor
from sklearn import metrics
from sklearn.multioutput import ClassifierChain
from sklearn.neighbors import KNeighborsClassifier 
from sklearn.model_selection import cross_val_predict, train_test_split  
from sklearn.preprocessing import StandardScaler    
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, mean_squared_error 
import glob as glob
import pickle

#id,sleep-hours,busyness,bp-systolic,bp-diastolic,heart-rate,target
#1,3,2,132,80,79,3

# ############################################################
#       Logistic Regression training and prediction with knn_data.csv
# ############################################################
class LOGISTIC_REGRESSION:
    def __init__(self):
        pass

    # print(sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[4],sys.argv[5]) # sleep-hour, busyness, bp-systolic, bp-diastolic, heart-rate
    running_with_java = 1
    if(running_with_java):
        
        cur_sleep = int(sys.argv[1])
        cur_busy = int(sys.argv[2])
        cur_bp_sys = int(sys.argv[3])
        cur_bp_dia = int(sys.argv[4])
        cur_hr = int(sys.argv[5])
    
        health_data = (np.array([[cur_sleep, cur_busy, cur_bp_sys, cur_bp_dia, cur_hr]]))
        cur_data = pd.DataFrame (health_data)
    
    dataset = pd.read_csv('src/main/resources/knn_data.csv', index_col="id")
    datatop = dataset.head()

    model_filename = 'src/main/resources/LogisticRegression_model.sav' # filename for the model
    # print(datatop)
  
    X = dataset.iloc[:, [1,2,3,4,5]].values # splits the data and make separate array X to hold attributes.
    y = dataset.iloc[:, -1].values  # splits the data and makes a separate array y to hold corresponding labels.
    k_split = 200   
    X_train = X[:k_split]
    y_train = y[:k_split]
    X_test = X[k_split:]
    y_test = y[k_split:]

    ##### Logistic Regression #####
    if(running_with_java == 0):
        reg = LogisticRegression(penalty='l2',random_state=None, solver='lbfgs', max_iter=10000, multi_class='auto').fit(X, y)
        #print("X_train: ", X_train.shape)
        #print("Y_train: ", y_train.shape)
    
        pickle.dump(reg, open(model_filename, 'wb'))
        
        y_pred = reg.predict(X_test)
        
        score = np.sum((y_pred - y_test)==0)/len(y_pred)
        print("accuracy score: ", score)
    
    
    if(running_with_java):
        X_test = health_data[0:]
        y_test = health_data[0:]

        reg = pickle.load(open(model_filename, 'rb'))
        y_pred = reg.predict(X_test)
        print(y_pred)    

    
if __name__ == '__main__':
    # File_setting()
    LOGISTIC_REGRESSION()