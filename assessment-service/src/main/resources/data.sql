-- Career Paths
INSERT INTO career_paths (name, description, required_skills, created_at) VALUES
('Full Stack Developer', 'Builds both frontend and backend of web applications', 'Programming,Database,Web Development', NOW()),
('Backend Developer', 'Specializes in server-side logic and APIs', 'Programming,Database,System Design', NOW()),
('Frontend Developer', 'Specializes in UI and user experience', 'Web Development,Programming', NOW()),
('Data Scientist', 'Analyzes data and builds ML models', 'Machine Learning,Programming,Database', NOW()),
('DevOps Engineer', 'Manages infrastructure, CI/CD, and deployments', 'Linux,Docker,Cloud,System Design', NOW()),
('Mobile Developer', 'Builds iOS and Android applications', 'Programming,Mobile Development', NOW()),
('System Design Engineer', 'Designs scalable distributed systems', 'System Design,Programming,Database', NOW()) ON CONFLICT DO NOTHING;

-- Categories
INSERT INTO categories (name, description, created_at) VALUES
('Programming Fundamentals', 'Tests core programming concepts and problem solving', NOW()),
('Database & SQL', 'Tests relational database design and query skills', NOW()),
('Web Development', 'Tests frontend and backend web technologies', NOW()),
('System Design', 'Tests ability to design scalable systems', NOW()),
('Data Structures & Algorithms', 'Tests DSA knowledge and complexity analysis', NOW()) ON CONFLICT DO NOTHING;

-- Programming Fundamentals Questions (12 questions, will randomly pick 5)
INSERT INTO questions (category_id, question_text, order_index, created_at) VALUES
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is the difference between stack and heap memory?', 1, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is polymorphism in object-oriented programming?', 2, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'Explain the concept of recursion with an example.', 3, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is the difference between == and .equals() in Java?', 4, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What are the four pillars of OOP?', 5, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is a NullPointerException and how do you prevent it?', 6, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is the difference between abstract class and interface?', 7, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'Explain method overloading vs method overriding.', 8, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is a design pattern? Name 3 common ones.', 9, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is the difference between checked and unchecked exceptions?', 10, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is multithreading and what problems can it cause?', 11, NOW()),
((SELECT id FROM categories WHERE name='Programming Fundamentals'), 'What is the SOLID principle?', 12, NOW()) ON CONFLICT DO NOTHING;

-- Options for Programming Fundamentals questions (4 options each, weights 0-3)
-- Question 1: Stack vs Heap
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the difference between stack and heap memory?'), 'Stack is for static memory allocation and heap for dynamic allocation', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the difference between stack and heap memory?'), 'Stack stores objects and heap stores primitives', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the difference between stack and heap memory?'), 'Stack is faster but limited in size, heap is larger but slower', 2, 3),
((SELECT id FROM questions WHERE question_text='What is the difference between stack and heap memory?'), 'They are the same thing with different names', 0, 4) ON CONFLICT DO NOTHING;

-- Question 2: Polymorphism
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is polymorphism in object-oriented programming?'), 'The ability of an object to take many forms', 3, 1),
((SELECT id FROM questions WHERE question_text='What is polymorphism in object-oriented programming?'), 'A way to hide data from other classes', 0, 2),
((SELECT id FROM questions WHERE question_text='What is polymorphism in object-oriented programming?'), 'Compile-time and runtime binding of methods', 2, 3),
((SELECT id FROM questions WHERE question_text='What is polymorphism in object-oriented programming?'), 'Only method overloading', 1, 4) ON CONFLICT DO NOTHING;

-- Question 3: Recursion
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Explain the concept of recursion with an example.'), 'A function that calls itself with a base case to stop', 3, 1),
((SELECT id FROM questions WHERE question_text='Explain the concept of recursion with an example.'), 'A loop that runs forever', 0, 2),
((SELECT id FROM questions WHERE question_text='Explain the concept of recursion with an example.'), 'Calling another function from within a function', 1, 3),
((SELECT id FROM questions WHERE question_text='Explain the concept of recursion with an example.'), 'A function that runs only once', 0, 4) ON CONFLICT DO NOTHING;

-- Question 4: == vs equals
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the difference between == and .equals() in Java?'), '== compares references, .equals() compares values', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the difference between == and .equals() in Java?'), 'They are identical', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the difference between == and .equals() in Java?'), '== compares values, .equals() compares references', 0, 3),
((SELECT id FROM questions WHERE question_text='What is the difference between == and .equals() in Java?'), '.equals() only works for strings', 1, 4) ON CONFLICT DO NOTHING;

-- Question 5: Four pillars of OOP
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What are the four pillars of OOP?'), 'Encapsulation, Inheritance, Polymorphism, Abstraction', 3, 1),
((SELECT id FROM questions WHERE question_text='What are the four pillars of OOP?'), 'Classes, Objects, Methods, Variables', 0, 2),
((SELECT id FROM questions WHERE question_text='What are the four pillars of OOP?'), 'Encapsulation, Inheritance, Polymorphism, Composition', 2, 3),
((SELECT id FROM questions WHERE question_text='What are the four pillars of OOP?'), 'Only Inheritance and Polymorphism', 0, 4) ON CONFLICT DO NOTHING;

-- Question 6: NullPointerException
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is a NullPointerException and how do you prevent it?'), 'Occurs when accessing a null reference; prevent with null checks or Optional', 3, 1),
((SELECT id FROM questions WHERE question_text='What is a NullPointerException and how do you prevent it?'), 'A compile-time error', 0, 2),
((SELECT id FROM questions WHERE question_text='What is a NullPointerException and how do you prevent it?'), 'Only occurs in C++', 0, 3),
((SELECT id FROM questions WHERE question_text='What is a NullPointerException and how do you prevent it?'), 'Occurs when memory is full', 1, 4) ON CONFLICT DO NOTHING;

-- Question 7: Abstract class vs Interface
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the difference between abstract class and interface?'), 'Abstract class can have state and implementation; interface is a contract', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the difference between abstract class and interface?'), 'They are exactly the same', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the difference between abstract class and interface?'), 'Interface can have constructors, abstract class cannot', 0, 3),
((SELECT id FROM questions WHERE question_text='What is the difference between abstract class and interface?'), 'Abstract class supports multiple inheritance', 2, 4) ON CONFLICT DO NOTHING;

-- Question 8: Overloading vs Overriding
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Explain method overloading vs method overriding.'), 'Overloading is compile-time polymorphism; overriding is runtime polymorphism', 3, 1),
((SELECT id FROM questions WHERE question_text='Explain method overloading vs method overriding.'), 'They are the same concept', 0, 2),
((SELECT id FROM questions WHERE question_text='Explain method overloading vs method overriding.'), 'Overriding changes method signature; overloading changes method body', 0, 3),
((SELECT id FROM questions WHERE question_text='Explain method overloading vs method overriding.'), 'Overloading only works with constructors', 1, 4) ON CONFLICT DO NOTHING;

-- Question 9: Design Patterns
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is a design pattern? Name 3 common ones.'), 'Reusable solutions to common problems; Singleton, Factory, Observer', 3, 1),
((SELECT id FROM questions WHERE question_text='What is a design pattern? Name 3 common ones.'), 'A way to draw UML diagrams', 0, 2),
((SELECT id FROM questions WHERE question_text='What is a design pattern? Name 3 common ones.'), 'Only used in frontend development', 0, 3),
((SELECT id FROM questions WHERE question_text='What is a design pattern? Name 3 common ones.'), 'Templates for writing classes', 1, 4) ON CONFLICT DO NOTHING;

-- Question 10: Checked vs Unchecked exceptions
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the difference between checked and unchecked exceptions?'), 'Checked must be handled at compile time; unchecked are runtime exceptions', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the difference between checked and unchecked exceptions?'), 'They are the same', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the difference between checked and unchecked exceptions?'), 'Unchecked exceptions must always be caught', 0, 3),
((SELECT id FROM questions WHERE question_text='What is the difference between checked and unchecked exceptions?'), 'Checked exceptions extend RuntimeException', 1, 4) ON CONFLICT DO NOTHING;

-- Question 11: Multithreading
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is multithreading and what problems can it cause?'), 'Running multiple threads concurrently; can cause race conditions and deadlocks', 3, 1),
((SELECT id FROM questions WHERE question_text='What is multithreading and what problems can it cause?'), 'Running multiple programs at once', 1, 2),
((SELECT id FROM questions WHERE question_text='What is multithreading and what problems can it cause?'), 'It has no problems', 0, 3),
((SELECT id FROM questions WHERE question_text='What is multithreading and what problems can it cause?'), 'Only available in C++', 0, 4) ON CONFLICT DO NOTHING;

-- Question 12: SOLID
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the SOLID principle?'), 'Single Responsibility, Open/Closed, Liskov, Interface Segregation, Dependency Inversion', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the SOLID principle?'), 'A security framework', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the SOLID principle?'), 'Only Single Responsibility and Open/Closed matter', 1, 3),
((SELECT id FROM questions WHERE question_text='What is the SOLID principle?'), 'A database design principle', 0, 4) ON CONFLICT DO NOTHING;

-- Database & SQL Category Questions (10 questions)
INSERT INTO questions (category_id, question_text, order_index, created_at) VALUES
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is the difference between INNER JOIN and LEFT JOIN?', 1, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is database normalization?', 2, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is a primary key vs foreign key?', 3, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is an index and why is it used?', 4, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is ACID in databases?', 5, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is the difference between SQL and NoSQL?', 6, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is a stored procedure?', 7, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'Explain the difference between DELETE, TRUNCATE, and DROP.', 8, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is a transaction in a database?', 9, NOW()),
((SELECT id FROM categories WHERE name='Database & SQL'), 'What is database sharding?', 10, NOW()) ON CONFLICT DO NOTHING;

-- Options for Database questions (4 options each)
INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the difference between INNER JOIN and LEFT JOIN?'), 'INNER JOIN returns matching rows only; LEFT JOIN returns all left table rows', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the difference between INNER JOIN and LEFT JOIN?'), 'They are the same', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the difference between INNER JOIN and LEFT JOIN?'), 'LEFT JOIN only returns non-matching rows', 0, 3),
((SELECT id FROM questions WHERE question_text='What is the difference between INNER JOIN and LEFT JOIN?'), 'INNER JOIN is slower than LEFT JOIN always', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is database normalization?'), 'Organizing data to reduce redundancy and improve integrity', 3, 1),
((SELECT id FROM questions WHERE question_text='What is database normalization?'), 'Making the database faster', 1, 2),
((SELECT id FROM questions WHERE question_text='What is database normalization?'), 'Adding more tables always', 0, 3),
((SELECT id FROM questions WHERE question_text='What is database normalization?'), 'Backing up the database', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is a primary key vs foreign key?'), 'Primary key uniquely identifies a row; foreign key references another table', 3, 1),
((SELECT id FROM questions WHERE question_text='What is a primary key vs foreign key?'), 'They are the same', 0, 2),
((SELECT id FROM questions WHERE question_text='What is a primary key vs foreign key?'), 'Foreign key must always be unique', 0, 3),
((SELECT id FROM questions WHERE question_text='What is a primary key vs foreign key?'), 'Primary key can have duplicate values', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is an index and why is it used?'), 'A data structure that speeds up data retrieval at cost of storage', 3, 1),
((SELECT id FROM questions WHERE question_text='What is an index and why is it used?'), 'A way to sort data permanently', 1, 2),
((SELECT id FROM questions WHERE question_text='What is an index and why is it used?'), 'Indexes slow down queries', 0, 3),
((SELECT id FROM questions WHERE question_text='What is an index and why is it used?'), 'Only used in NoSQL databases', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is ACID in databases?'), 'Atomicity, Consistency, Isolation, Durability — transaction properties', 3, 1),
((SELECT id FROM questions WHERE question_text='What is ACID in databases?'), 'A type of database engine', 0, 2),
((SELECT id FROM questions WHERE question_text='What is ACID in databases?'), 'A query optimization technique', 0, 3),
((SELECT id FROM questions WHERE question_text='What is ACID in databases?'), 'Atomicity and Consistency only', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the difference between SQL and NoSQL?'), 'SQL is relational with fixed schema; NoSQL is flexible schema for unstructured data', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the difference between SQL and NoSQL?'), 'NoSQL is always faster', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the difference between SQL and NoSQL?'), 'SQL cannot handle large data', 0, 3),
((SELECT id FROM questions WHERE question_text='What is the difference between SQL and NoSQL?'), 'They are interchangeable', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is a stored procedure?'), 'Precompiled SQL code stored in the database for reuse', 3, 1),
((SELECT id FROM questions WHERE question_text='What is a stored procedure?'), 'A backup procedure', 0, 2),
((SELECT id FROM questions WHERE question_text='What is a stored procedure?'), 'A Java method that calls SQL', 1, 3),
((SELECT id FROM questions WHERE question_text='What is a stored procedure?'), 'Only available in Oracle', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Explain the difference between DELETE, TRUNCATE, and DROP.'), 'DELETE removes rows with WHERE; TRUNCATE removes all rows; DROP removes table', 3, 1),
((SELECT id FROM questions WHERE question_text='Explain the difference between DELETE, TRUNCATE, and DROP.'), 'They all do the same thing', 0, 2),
((SELECT id FROM questions WHERE question_text='Explain the difference between DELETE, TRUNCATE, and DROP.'), 'DROP only removes data not structure', 0, 3),
((SELECT id FROM questions WHERE question_text='Explain the difference between DELETE, TRUNCATE, and DROP.'), 'TRUNCATE can be rolled back', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is a transaction in a database?'), 'A unit of work that is atomic — either fully completes or fully rolls back', 3, 1),
((SELECT id FROM questions WHERE question_text='What is a transaction in a database?'), 'A SQL SELECT statement', 0, 2),
((SELECT id FROM questions WHERE question_text='What is a transaction in a database?'), 'A way to connect to the database', 0, 3),
((SELECT id FROM questions WHERE question_text='What is a transaction in a database?'), 'Only used in banking applications', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is database sharding?'), 'Splitting a database into smaller pieces distributed across servers', 3, 1),
((SELECT id FROM questions WHERE question_text='What is database sharding?'), 'Encrypting database data', 0, 2),
((SELECT id FROM questions WHERE question_text='What is database sharding?'), 'Creating database backups', 0, 3),
((SELECT id FROM questions WHERE question_text='What is database sharding?'), 'A type of database index', 1, 4) ON CONFLICT DO NOTHING;

-- ==========================================================================================
-- Career-readiness assessment: Aptitude / Domain Knowledge / Soft Skills.
-- These 3 categories back the fixed 3-section flow driven by AssessmentSection -- the client
-- never requests a categoryId directly for this flow, only a section name, and the section
-- resolves to one of a pool of categories (see AssessmentConstants.SECTION_CATEGORY_POOL).
-- Domain Knowledge's pool also includes Programming Fundamentals and Database & SQL above, so
-- a retake can land on any of the three -- this new category is simply a third option sized to
-- match (10 questions), keeping the "20 questions total" invariant true regardless of which
-- pool member gets picked.
-- ==========================================================================================
INSERT INTO categories (name, description, created_at) VALUES
('Aptitude', 'Logic, numbers and pattern recognition under time pressure', NOW()),
('Domain Knowledge', 'General technical and analytical domain knowledge', NOW()),
('Soft Skills', 'How you handle ambiguity, disagreement and stakeholders', NOW()) ON CONFLICT DO NOTHING;

-- Aptitude Questions (5 questions)
INSERT INTO questions (category_id, question_text, order_index, created_at) VALUES
((SELECT id FROM categories WHERE name='Aptitude'), 'A dataset of 5,000 rows has 12% missing values in one column. What do you do first?', 1, NOW()),
((SELECT id FROM categories WHERE name='Aptitude'), 'Revenue grew 8% while orders grew 20%. What most likely happened?', 2, NOW()),
((SELECT id FROM categories WHERE name='Aptitude'), 'Which SQL clause filters rows after aggregation?', 3, NOW()),
((SELECT id FROM categories WHERE name='Aptitude'), 'A dashboard loads in 40 seconds. Where do you look first?', 4, NOW()),
((SELECT id FROM categories WHERE name='Aptitude'), 'You have 90 seconds to pick the odd one out: 3, 8, 15, 24, 36. Which is it?', 5, NOW()) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A dataset of 5,000 rows has 12% missing values in one column. What do you do first?'), 'Check whether the missingness correlates with another column', 3, 1),
((SELECT id FROM questions WHERE question_text='A dataset of 5,000 rows has 12% missing values in one column. What do you do first?'), 'Drop every row with a missing value', 1, 2),
((SELECT id FROM questions WHERE question_text='A dataset of 5,000 rows has 12% missing values in one column. What do you do first?'), 'Fill the gaps with the column mean', 2, 3),
((SELECT id FROM questions WHERE question_text='A dataset of 5,000 rows has 12% missing values in one column. What do you do first?'), 'Ignore the column entirely', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Revenue grew 8% while orders grew 20%. What most likely happened?'), 'Average order value fell', 3, 1),
((SELECT id FROM questions WHERE question_text='Revenue grew 8% while orders grew 20%. What most likely happened?'), 'Margins improved', 0, 2),
((SELECT id FROM questions WHERE question_text='Revenue grew 8% while orders grew 20%. What most likely happened?'), 'Customer count fell', 1, 3),
((SELECT id FROM questions WHERE question_text='Revenue grew 8% while orders grew 20%. What most likely happened?'), 'Prices rose', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Which SQL clause filters rows after aggregation?'), 'WHERE', 0, 1),
((SELECT id FROM questions WHERE question_text='Which SQL clause filters rows after aggregation?'), 'GROUP BY', 0, 2),
((SELECT id FROM questions WHERE question_text='Which SQL clause filters rows after aggregation?'), 'HAVING', 3, 3),
((SELECT id FROM questions WHERE question_text='Which SQL clause filters rows after aggregation?'), 'ORDER BY', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A dashboard loads in 40 seconds. Where do you look first?'), 'The query behind it', 3, 1),
((SELECT id FROM questions WHERE question_text='A dashboard loads in 40 seconds. Where do you look first?'), 'The chart library', 0, 2),
((SELECT id FROM questions WHERE question_text='A dashboard loads in 40 seconds. Where do you look first?'), 'The browser', 1, 3),
((SELECT id FROM questions WHERE question_text='A dashboard loads in 40 seconds. Where do you look first?'), 'The colour palette', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='You have 90 seconds to pick the odd one out: 3, 8, 15, 24, 36. Which is it?'), '36', 3, 1),
((SELECT id FROM questions WHERE question_text='You have 90 seconds to pick the odd one out: 3, 8, 15, 24, 36. Which is it?'), '24', 0, 2),
((SELECT id FROM questions WHERE question_text='You have 90 seconds to pick the odd one out: 3, 8, 15, 24, 36. Which is it?'), '15', 0, 3),
((SELECT id FROM questions WHERE question_text='You have 90 seconds to pick the odd one out: 3, 8, 15, 24, 36. Which is it?'), '8', 0, 4) ON CONFLICT DO NOTHING;

-- Domain Knowledge Questions (10 questions)
INSERT INTO questions (category_id, question_text, order_index, created_at) VALUES
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'A metric moved from 4.2 to 4.8 week over week. What do you report first?', 1, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'What does a primary key guarantee on a table?', 2, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'An A/B test shows a 2% lift with a p-value of 0.18. What do you do?', 3, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'Which term describes data collected without random assignment?', 4, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'A join is returning more rows than either source table. Most likely cause?', 5, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'What does "statistically significant" actually tell you?', 6, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'Which best separates correlation from causation?', 7, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'A KPI dashboard is used by execs weekly. What matters most in its design?', 8, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'What is the main risk of survivorship bias in a churn analysis?', 9, NOW()),
((SELECT id FROM categories WHERE name='Domain Knowledge'), 'Two teams report different values for "active users." First move?', 10, NOW()) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A metric moved from 4.2 to 4.8 week over week. What do you report first?'), 'The size of the move and what changed underneath it', 3, 1),
((SELECT id FROM questions WHERE question_text='A metric moved from 4.2 to 4.8 week over week. What do you report first?'), 'That it went up', 1, 2),
((SELECT id FROM questions WHERE question_text='A metric moved from 4.2 to 4.8 week over week. What do you report first?'), 'A forecast for next week', 0, 3),
((SELECT id FROM questions WHERE question_text='A metric moved from 4.2 to 4.8 week over week. What do you report first?'), 'Nothing until month end', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What does a primary key guarantee on a table?'), 'Every row is uniquely identifiable', 3, 1),
((SELECT id FROM questions WHERE question_text='What does a primary key guarantee on a table?'), 'Every column is indexed', 0, 2),
((SELECT id FROM questions WHERE question_text='What does a primary key guarantee on a table?'), 'The table has no duplicates in any column', 0, 3),
((SELECT id FROM questions WHERE question_text='What does a primary key guarantee on a table?'), 'Queries run faster', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='An A/B test shows a 2% lift with a p-value of 0.18. What do you do?'), 'Treat it as inconclusive and extend the test', 3, 1),
((SELECT id FROM questions WHERE question_text='An A/B test shows a 2% lift with a p-value of 0.18. What do you do?'), 'Ship it, 2% is 2%', 0, 2),
((SELECT id FROM questions WHERE question_text='An A/B test shows a 2% lift with a p-value of 0.18. What do you do?'), 'Kill the test immediately', 0, 3),
((SELECT id FROM questions WHERE question_text='An A/B test shows a 2% lift with a p-value of 0.18. What do you do?'), 'Re-run with half the sample', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Which term describes data collected without random assignment?'), 'Observational', 3, 1),
((SELECT id FROM questions WHERE question_text='Which term describes data collected without random assignment?'), 'Experimental', 0, 2),
((SELECT id FROM questions WHERE question_text='Which term describes data collected without random assignment?'), 'Longitudinal', 1, 3),
((SELECT id FROM questions WHERE question_text='Which term describes data collected without random assignment?'), 'Synthetic', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A join is returning more rows than either source table. Most likely cause?'), 'A one-to-many relationship fanning out', 3, 1),
((SELECT id FROM questions WHERE question_text='A join is returning more rows than either source table. Most likely cause?'), 'A missing WHERE clause', 1, 2),
((SELECT id FROM questions WHERE question_text='A join is returning more rows than either source table. Most likely cause?'), 'A corrupted index', 0, 3),
((SELECT id FROM questions WHERE question_text='A join is returning more rows than either source table. Most likely cause?'), 'Too many columns selected', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What does "statistically significant" actually tell you?'), 'The result is unlikely to be due to chance alone', 3, 1),
((SELECT id FROM questions WHERE question_text='What does "statistically significant" actually tell you?'), 'The effect is large', 0, 2),
((SELECT id FROM questions WHERE question_text='What does "statistically significant" actually tell you?'), 'The result will replicate', 0, 3),
((SELECT id FROM questions WHERE question_text='What does "statistically significant" actually tell you?'), 'The sample was random', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Which best separates correlation from causation?'), 'A controlled experiment with random assignment', 3, 1),
((SELECT id FROM questions WHERE question_text='Which best separates correlation from causation?'), 'A larger sample size', 1, 2),
((SELECT id FROM questions WHERE question_text='Which best separates correlation from causation?'), 'A higher correlation coefficient', 0, 3),
((SELECT id FROM questions WHERE question_text='Which best separates correlation from causation?'), 'More decimal places', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A KPI dashboard is used by execs weekly. What matters most in its design?'), 'A stable definition they can trust over time', 3, 1),
((SELECT id FROM questions WHERE question_text='A KPI dashboard is used by execs weekly. What matters most in its design?'), 'The widest possible set of charts', 0, 2),
((SELECT id FROM questions WHERE question_text='A KPI dashboard is used by execs weekly. What matters most in its design?'), 'Real-time refresh', 1, 3),
((SELECT id FROM questions WHERE question_text='A KPI dashboard is used by execs weekly. What matters most in its design?'), 'Matching brand colours', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='What is the main risk of survivorship bias in a churn analysis?'), 'You only study customers still around to be studied', 3, 1),
((SELECT id FROM questions WHERE question_text='What is the main risk of survivorship bias in a churn analysis?'), 'The sample is too large', 0, 2),
((SELECT id FROM questions WHERE question_text='What is the main risk of survivorship bias in a churn analysis?'), 'The data is too recent', 1, 3),
((SELECT id FROM questions WHERE question_text='What is the main risk of survivorship bias in a churn analysis?'), 'The metric is mislabeled', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Two teams report different values for "active users." First move?'), 'Compare their definitions before comparing their numbers', 3, 1),
((SELECT id FROM questions WHERE question_text='Two teams report different values for "active users." First move?'), 'Trust the larger number', 0, 2),
((SELECT id FROM questions WHERE question_text='Two teams report different values for "active users." First move?'), 'Trust the more recent report', 1, 3),
((SELECT id FROM questions WHERE question_text='Two teams report different values for "active users." First move?'), 'Average the two', 0, 4) ON CONFLICT DO NOTHING;

-- Soft Skills Questions (5 questions)
INSERT INTO questions (category_id, question_text, order_index, created_at) VALUES
((SELECT id FROM categories WHERE name='Soft Skills'), 'A stakeholder disputes your numbers in a meeting. You:', 1, NOW()),
((SELECT id FROM categories WHERE name='Soft Skills'), 'A deadline is at risk because a dependency slipped. You:', 2, NOW()),
((SELECT id FROM categories WHERE name='Soft Skills'), 'Two colleagues disagree on an approach in front of you. You:', 3, NOW()),
((SELECT id FROM categories WHERE name='Soft Skills'), 'You receive blunt, critical feedback on work you were proud of. You:', 4, NOW()),
((SELECT id FROM categories WHERE name='Soft Skills'), 'A new teammate keeps asking questions you consider basic. You:', 5, NOW()) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A stakeholder disputes your numbers in a meeting. You:'), 'Ask which number they expected and reconcile after', 3, 1),
((SELECT id FROM questions WHERE question_text='A stakeholder disputes your numbers in a meeting. You:'), 'Defend the figure immediately', 0, 2),
((SELECT id FROM questions WHERE question_text='A stakeholder disputes your numbers in a meeting. You:'), 'Agree to recheck and move on', 1, 3),
((SELECT id FROM questions WHERE question_text='A stakeholder disputes your numbers in a meeting. You:'), 'Escalate to your manager', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A deadline is at risk because a dependency slipped. You:'), 'Flag it early with a revised plan', 3, 1),
((SELECT id FROM questions WHERE question_text='A deadline is at risk because a dependency slipped. You:'), 'Work nights to absorb the slip quietly', 1, 2),
((SELECT id FROM questions WHERE question_text='A deadline is at risk because a dependency slipped. You:'), 'Wait to see if it resolves itself', 0, 3),
((SELECT id FROM questions WHERE question_text='A deadline is at risk because a dependency slipped. You:'), 'Blame the dependency owner publicly', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='Two colleagues disagree on an approach in front of you. You:'), 'Ask each to state the tradeoff they care about most', 3, 1),
((SELECT id FROM questions WHERE question_text='Two colleagues disagree on an approach in front of you. You:'), 'Pick a side to end the discussion', 0, 2),
((SELECT id FROM questions WHERE question_text='Two colleagues disagree on an approach in front of you. You:'), 'Stay silent and let them settle it', 1, 3),
((SELECT id FROM questions WHERE question_text='Two colleagues disagree on an approach in front of you. You:'), 'Suggest a manager decide', 0, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='You receive blunt, critical feedback on work you were proud of. You:'), 'Ask for specifics and separate the useful parts', 3, 1),
((SELECT id FROM questions WHERE question_text='You receive blunt, critical feedback on work you were proud of. You:'), 'Explain why the criticism is wrong', 0, 2),
((SELECT id FROM questions WHERE question_text='You receive blunt, critical feedback on work you were proud of. You:'), 'Redo everything without asking why', 0, 3),
((SELECT id FROM questions WHERE question_text='You receive blunt, critical feedback on work you were proud of. You:'), 'Let it go without acting on it', 1, 4) ON CONFLICT DO NOTHING;

INSERT INTO options (question_id, option_text, weight, order_index) VALUES
((SELECT id FROM questions WHERE question_text='A new teammate keeps asking questions you consider basic. You:'), 'Answer clearly and point them to where to self-serve next time', 3, 1),
((SELECT id FROM questions WHERE question_text='A new teammate keeps asking questions you consider basic. You:'), 'Answer but show visible impatience', 1, 2),
((SELECT id FROM questions WHERE question_text='A new teammate keeps asking questions you consider basic. You:'), 'Redirect them to someone else', 0, 3),
((SELECT id FROM questions WHERE question_text='A new teammate keeps asking questions you consider basic. You:'), 'Ignore the questions until they stop asking', 0, 4) ON CONFLICT DO NOTHING;
