#!/usr/bin/python

import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import sklearn
import seaborn as sns
from sklearn.multioutput import ClassifierChain
from sklearn.neighbors import KNeighborsClassifier 
from sklearn.model_selection import train_test_split  
from sklearn.preprocessing import StandardScaler    
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix  

#################################################################
#       csv file read and write 
#       create data file with target (stress level)
#       for knn training
##################################################################
class File_setting:
    def __init__(self):
        print("this is file data setting code")
       
    import csv
    filepath = 'src/main/resources/big_heart_rate.csv'
    header = []
    target = []
    id = []
    heartrate = []
    var=[]
    try: 
        with open(filepath, newline='') as csvfile:
            manual_reader =  csv.reader(csvfile, delimiter=',')
            header = next(manual_reader)
            print(header)
            level = 1
            for row in manual_reader:
                #print(', '.join(row)) 
                row[0] = int(row[0]) # id
                row[1] = int(row[1]) # heart-rate
                row[2] = int(row[2]) # variance
                id.append(row[0])
                heartrate.append(row[1])
                var.append(row[2])
                
                if row[1] >= 80 and row[2] >= 25 :
                    level = level+1
                elif row[1] >= 90 and row[2] >= 25 :
                    level = level+1 
                elif row[2] <= -25 :
                    level = level-1
                
                if(level > 5): level = 5
                elif(level< 1): level = 1
                
                target.append(level)
            
    finally:
        csvfile.close()    
        
    try:
        with open('src/main/resources/hr_target.csv', 'w', newline='') as csvfile:       
            manual_writer = csv.writer(csvfile, delimiter=',')
            manual_writer.writerow(["id", "heart-rate", "variance", "target"])
            zip_all = zip(id, heartrate, var, target)
            #r =  zip (id, heartrate, var, target)
            for l in zip_all :
                manual_writer.writerow(l)
    finally:
        csvfile.close()    


#######################################################
#       knn training with hr_data.csv
########################################################
class KNN:
    # read dataset for knn
    def __init__(self):
        print(" ")

    #dataset = pd.read_csv('src/main/resources/example3.csv', index_col="id")
    dataset = pd.read_csv('src/main/resources/hr_target.csv', index_col="id")
    dataset.head()
    # print("colum 0: ", dataset.columns[0])
    # print("colum 2: ", dataset.columns[2])
    # print("colum 3: ", dataset.columns[3])
    
    X = dataset.iloc[:, [0]].values # splits the data and make separate array X to hold attributes.
    y = dataset.iloc[:, -1].values  # splits the data and makes a separate array y to hold corresponding labels.
    # print(X.shape)
    # print(y.shape)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.20, random_state=0) # training 80%, testing 20%
    knn = KNeighborsClassifier(n_neighbors = 77, p = 2) 
    # p = 1 , Manhattan Distance
    # p = 2 , Euclidean Distance
    # p = infinity , Cheybchev Distance
    
    knn.fit(X_train, y_train)
    y_pred = knn.predict(X_test)
    print("accuracy score: " , accuracy_score(y_test, y_pred))
    print(classification_report(y_test, y_pred))
    #print(confusion_matrix(y_test, y_pred))


if __name__ == '__main__':
    #File_setting()
    KNN()