# CSC 254 Project 5: Concurrency

Implemented anti-deadlock strategies to the classic Dining Philosophers Problem. 
Project link can be found here: https://www.cs.rochester.edu/courses/254/fall2019/assignments/java.shtml


Tongtong Qiu and Rukimani PV 
Monday, November 25th, 2019 


To Run:

javac Dining.java 
java Dining -v > test1.txt 
python3 check_output.py test1.txt

Extra Credit:

Improved graphics of the project- specifically used "Image" to add images of forks, then added spaghetti at the center of the panel. Also, changed the buttons and layout of the bottom.

B/c the philosopher class doesn't extend JPanel, the other draw function wouldn't work b/c Image Observer could not be accessed in this class. 

Logic:

In this project, we used Reentrant locks to resolve the Dining Philosophers Problem, specifically we used the "tryLock()" method under the reentrant lock, which "acquires the lock only if it is not held by another thread at the time of invocation." That is, the thread will only acquire the lock if it is available and not held by any other thread. Essentially, we created two booleans that would keep track of whether the left fork or right is being held- this was then used to resolve the deadlock issues, by checking to see if the right and left fork were both being held or not. We added unlock() and lock() methods to lock the left or right fork (respectively) when it was in use or not. From Professor Scott's OH, we found that deadlock was the only issue we needed to explicitly handle after the other corrections took place- that is, adding booleans for the left and right fork, etc. 

Comments provided on the lines added for further details.

Attached is a screenshot of our results after running for 10 minutes. 
