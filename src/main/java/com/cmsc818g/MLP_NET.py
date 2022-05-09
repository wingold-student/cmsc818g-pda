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
from sklearn.neural_network import MLPClassifier
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, mean_squared_error 
import glob as glob
from sklearn.preprocessing import OneHotEncoder

#id,sleep-hours,busyness,bp-systolic,bp-diastolic,heart-rate,target
#1,3,2,132,80,79,3

# ############################################################
#       Multi-Layer Perceptron training and prediction with knn_data.csv
# ############################################################

def convertToOneHot(vector, num_classes=None):
    """
    Converts an input 1-D vector of integers into an output
    2-D array of one-hot vectors, where an i'th input value
    of j will set a '1' in the i'th row, j'th column of the
    output array.

    Example:
        v = np.array((1, 0, 4))
        one_hot_v = convertToOneHot(v)
        print one_hot_v

        [[0 1 0 0 0]
         [1 0 0 0 0]
         [0 0 0 0 1]]
    """

    assert isinstance(vector, np.ndarray)
    assert len(vector) > 0

    if num_classes is None:
        num_classes = np.max(vector)+1
    else:
        assert num_classes > 0
        assert num_classes >= np.max(vector)

    result = np.zeros(shape=(len(vector), num_classes))
    result[np.arange(len(vector)), vector] = 1
    return result.astype(int)

def converttoLabel(y_pred):
    label = []
    for i in range(y_pred.shape[0]):
        label.append(np.argmax(y_pred[i,:]))
    return np.array(label) +1

class MLP_Net:
    def __init__(self):
        pass

    # print(sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[4],sys.argv[5]) # sleep-hour, busyness, bp-systolic, bp-diastolic, heart-rate
    running_with_java = 0
    if(running_with_java):
        df
        cur_sleep = int(sys.argv[1])
        cur_busy = int(sys.argv[2])
        cur_bp_sys = int(sys.argv[3])
        cur_bp_dia = int(sys.argv[4])
        cur_hr = int(sys.argv[5])
    
        health_data = (np.array([[cur_sleep, cur_busy, cur_bp_sys, cur_bp_dia, cur_hr]]))
        cur_data = pd.DataFrame (health_data)
    
    dataset = pd.read_csv('../../../../../src/main/resources/knn_data.csv', index_col="id")
    datatop = dataset.head()
    # print(datatop)
  
    X = dataset.iloc[:, [1,2,3,4,5]].values # splits the data and make separate array X to hold attributes.
    y = dataset.iloc[:, -1].values  # splits the data and makes a separate array y to hold corresponding labels.
    #### Converting to one hot encoding for multi-class classification
    y = convertToOneHot(y-1, num_classes=5)
    
    
    k_split = 200   
    X_train = X[:k_split]
    y_train = y[:k_split]
    X_test = X[k_split:]
    y_test = y[k_split:]
    # print(y[k_split:])
    if(running_with_java):
        X_test = health_data[0:]
        y_test = health_data[0:]
        y_test = convertToOneHot(y_test-1, num_classes=5)

    ##### Multi-Layer Perceptron #####
    clf = MLPClassifier(solver='lbfgs', alpha=1e-5, hidden_layer_sizes=(50,), max_iter=10000, random_state=1)
    clf.fit(X, y)
    #print("X_train: ", X_train.shape)
    #print("Y_train: ", y_train.shape)
    
    y_pred = clf.predict_proba(X_test)
    #### converting predict class probabilites to class labels
    label = converttoLabel(y_pred)
    y_test = converttoLabel(y_test)
    y_pred = label
    

    if(running_with_java == 0):
        score = np.sum((y_pred - y_test)==0)/len(y_pred)
        print("accuracy score: ", score)
    
    if(running_with_java):
        print(y_pred)    

    
if __name__ == '__main__':
    # File_setting()
    MLP_Net()