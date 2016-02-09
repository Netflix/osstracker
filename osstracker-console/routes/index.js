var express = require('express');
var log4js = require('log4js');
var request = require('request');
var cassandra = require('cassandra-driver');

var router = express.Router();

var CASS_HOST = process.env.CASS_HOST;
var CASS_PORT = process.env.CASS_PORT;
var ES_HOST = process.env.ES_HOST;
var ES_PORT = process.env.ES_PORT;

var logger = log4js.getLogger();
logger.setLevel('INFO');
var dbClient;
var esHosts;

var SELECT_ALL_FROM_REPO_ORGS = "SELECT * FROM repo_orgs";
var INSERT_INTO_REPOS = "INSERT INTO repo_info (gh_repo_name, org_short, dev_lead_empid, mgr_lead_empid) VALUES (?, ?, ?, ?)";
var SELECT_ALL_FROM_REPO_OWNERSHIP = "SELECT * FROM repo_info";
var SELECT_REPOS_FROM_REPO_OWNERSHIP = "SELECT gh_repo_name FROM repo_info";

router.get('/hosts/eshost', function(req, res, next) {
    res.send(esHosts[0])
});

//
// Reponse is JSON list that has repo items with repo name, repo org (short form),
// netflix ids for manager lead and development lead
//
// [ { name: 'somerepo', orgName: 'CPIE', mgrLead: "12345", devLead: "56789" }, ... ]
//
router.get('/repos', function(req, res, next) {
    dbClient.execute(SELECT_ALL_FROM_REPO_OWNERSHIP, [], {prepare: true}, function(err, result) {
        if (err) {
            logger.debug('error ' + JSON.stringify(err));
            res.status(500).end();
            return;
        }

        var repos = [];
        for (ii = 0; ii < result.rows.length; ii++) {
            var repo = {
                name : result.rows[ii].gh_repo_name,
                orgName : result.rows[ii].org_short,
                mgrLead: result.rows[ii].mgr_lead_empid,
                devLead: result.rows[ii].dev_lead_empid
            }
            repos.push(repo);
        }

        res.send(repos);
    });
});

function getRepos(callback/*(repos, err)*/) {
    dbClient.execute(SELECT_REPOS_FROM_REPO_OWNERSHIP, [], {prepare: true}, function(err, result) {
        if (err) {
            logger.debug('error ' + JSON.stringify(err));
            callback(null, err);
            return;
        }

        var repos = [];
        for (ii = 0; ii < result.rows.length; ii++) {
            repos.push(result.rows[ii].gh_repo_name);
        }

        callback(repos);
    });
}

//
// Response is JSON list that has users with
// [ { employeeId: '123456', githubId: 'githubusername', email: 'user@netflix.com',
//     name: 'Joe Smith' } ... ]
//
router.get('/users', function(req, res, next) {
    var fakeResponse = [
        {"employeeId":"111111","githubId":"ghId1","email":"user1@netflix.com","name":"User One"},
        {"employeeId":"222222","githubId":"ghId2","email":"user2@netflix.com","name":"User Two"}
    ];
    res.send(fakeResponse);
    // TODO: Generalize the below
    //var url = 'some internal whitepages app';
    //var qArgs = { method: 'GET', uri: url};
    //
    //request(qArgs, function (err, response, body) {
    //    if (err) {
    //        logger.error('error = ' + JSON.stringify(err));
    //        res.status(500).end();
    //        return;
    //    }
    //    else {
    //        var wpGHUsers;
    //        try {
    //            wpGHUsers = JSON.parse(response.body);
    //        } catch (e) {
    //            logger.error('error parsing response');
    //            res.status(500).end();
    //            return;
    //        }
    //        logger.debug('users = ' + JSON.stringify(wpGHUsers));
    //        var simpleUsers = [];
    //        for (ii = 0; ii < wpGHUsers.length; ii++) {
    //            simpleUsers.push({
    //                employeeId: wpGHUsers[ii].empId,
    //                githubId: wpGHUsers[ii].empGhId,
    //                email: wpGHUsers[ii].empEmail,
    //                name: wpGHUsers[ii].empFirstName + ' ' + wpGHUsers[ii].empLastName
    //            });
    //        }
    //        res.send(simpleUsers);
    //    }
    //});
});

//
// Response is JSON list that has orgs with
// [ { orgName: 'CPIE', orgDesc: 'Cloud Platform Infrastructure Engineering' } ... ]
//
router.get('/repos/orgs', function(req, res, next) {
	dbClient.execute(SELECT_ALL_FROM_REPO_ORGS, [], {prepare: true}, function(err, result) {
		if (err) {
            logger.debug('error ' + JSON.stringify(err));
			res.status(500).end();
			return;
		}
		var orgs = []
		for (ii = 0; ii < result.rows.length; ii++) {
			var org = {
				"orgName" : result.rows[ii].org_short,
				"orgDesc" : result.rows[ii].org_description
			}
			orgs.push(org);
		}
		res.send(orgs)
		return;
	});
});

//
// Expects repoName, repoOrg, mgrLead (netflixId), devLead (netflixId)
//
router.post('/repos/:repoName', function(req, res) {
    var repoName = req.params.repoName;
    var repoOrg = req.body.repoOrg;
    var repoMgrLead = req.body.mgrLead;
    var repoDevLead = req.body.devLead;
    
    var params = [repoName, repoOrg, repoDevLead, repoMgrLead];
    logger.debug(INSERT_INTO_REPOS + ' ' + params);
    dbClient.execute(INSERT_INTO_REPOS, params, {prepare: true}, function(err) {
        if (err) {
            logger.error("err = " + JSON.stringify(err));
            res.status(500).end();
            return;
        }
    });
    res.status(200).end();
    return;
});

//
//Response is JSON list that has orgs with
// { lastUpdated: dateOfLastUpdate, repos : [
//   { repo: 'reponame', metric1: metric1val, metric2: metric2val } ...
//     ]
// }
router.get('/repos/stats', function (req, res) {
    queryLatestStats(function(err, allrepos) {
        if (err) {
            logger.error("err = " + JSON.stringify(err));
            res.status(500).end();
            return;
        }

        var repos = [];
        var therepos = allrepos.repos
        for (ii = 0; ii < therepos.length; ii++) {
            var therepo = therepos[ii];
            var repo = {
                name: therepo.repo_name,
                forks: therepo.forks,
                stars: therepo.stars,
                numContributors: therepo.numContributors,
                issueOpenCount: therepo.issues.openCount,
                issueClosedCount: therepo.issues.closedCount,
                issueAvgClose: therepo.issues.avgTimeToCloseInDays,
                prOpenCount: therepo.pullRequests.openCount,
                prClosedCount: therepo.pullRequests.closedCount,
                prAvgClose: therepo.pullRequests.avgTimeToCloseInDays,
                daysSinceLastCommit: therepo.commits.daysSinceLastCommit,
                public: therepo.public,
                osslifecycle: therepo.osslifecycle
            };
            repos.push(repo);
        }
        res.send(repos);
    });
});

router.get('/repos/overview', function (req, res) {
    queryLatestStats(function(err, allrepos) {
        if (err) {
            logger.error("err = " + JSON.stringify(err));
            res.status(500).end();
            return;
        }
        res.send(allrepos);
    });
});

router.get('/repos/:repoName/stats', function (req, res) {
    var repoName = req.param('repoName');
    queryAllStats(repoName, function(err, hits) {
       if (err) {
           logger.error("err = " + err);
           res.status(500).end();
           return;
       }
       var stats = []
       logger.debug('hits = ' + hits);
       for (ii = 0; ii < hits.length; ii++) {
           var stat = {
               date : hits[ii]._source.asOfSimple,
               name: hits[ii]._source.repos.name,
               forks: hits[ii]._source.repos.forks,
               stars: hits[ii]._source.repos.forks,
               numContributors: hits[ii]._source.repos.numContributors,
               issueOpenCount: hits[ii]._source.repos.issues.openCount,
               issueClosedCount: hits[ii]._source.repos.issues.closedCount,
               issueAvgClose: hits[ii]._source.repos.issues.avgTimeToCloseInDays,
               prOpenCount: hits[ii]._source.repos.pullRequests.openCount,
               prClosedCount: hits[ii]._source.repos.pullRequests.closedCount,
               prAvgClose: hits[ii]._source.repos.pullRequests.avgTimeToCloseInDays,
               daysSinceLastCommit: hits[ii]._source.repos.commits.daysSinceLastCommit
           }
           stats.push(stat);
       }
       res.send(stats);
    });
});

function queryAllStats(repoName, callback/*(err, hits)*/) {
    var esHost = esHosts[0];
    // query to search for a specific repo returning only the last document (date wise)
    var query = { "size": 1, "sort": [{"asOfYYYYMMDD": {"order": "desc"}}]};
    var url = 'http://' + esHost + ':7104/osstracker/allrepos_stats/_search';
    var qArgs = { method: 'POST', uri: url, json: query};
    request(qArgs, function (err, response, body) {
        if (err) {
            logger.error('error = ' + err);
            callback(err, null);
            return;
        }
        else {
            if (response.statusCode == 200) {
                callback(null, body.hits.hits[0]);
                return;
            }
            else {
                logger.error('error status code = ' + response.statusCode);
                callback('error status code = ' + response.statusCode, null);
                return;
            }
        }
    });
}

function queryLatestStats(callback/*(err, stats)*/) {
    var esHost = esHosts[0];
    // query to search for a specific repo returning only the last document (date wise)
    var query = { "size": 1, "sort": [{"asOfYYYYMMDD": {"order": "desc"}}]};
    var url = 'http://' + esHost + ':7104/osstracker/allrepos_stats/_search';

    var qArgs = { method: 'POST', uri: url, json: query};
    request(qArgs, function (err, response, body) {
        if (err) {
            logger.error('error = ' + err);
            callback(err, null);
            return;
        }
        else {
            logger.debug("response = " + JSON.stringify(body));
            if (response.statusCode == 200) {
                callback(null, body.hits.hits[0]._source);
                return;
            }
            else {
                logger.error('error status code = ' + response.statusCode);
                callback('error status code = ' + response.statusCode, null);
                return;
            }
        }
    });
}

function connectToDataBase(hosts, callback/*(err, dbClient)*/) {
    logger.info("hosts = " + hosts)
	client = new cassandra.Client({ contactPoints: hosts, protocolOptions : { port : 7104 }, keyspace: 'osstracker'});
	if (!client) {
		callback("error connecting to database", null);
	}
	else {
		logger.info("database client = " + client);
		callback(null, client);
	}
}

function getDBClient() {
    connectToDataBase([CASS_HOST], function(err, client) {
        if (err) {
            logger.info("could not get database connection, waiting");
        }
        else {
            dbClient = client;
        }
    });
}

function getESHosts() {
    logger.info("es connect hosts " + ES_HOST)
    esHosts = [ES_HOST];
}

var waitForDbConnections = setInterval(function () {
	if (dbClient && esHosts) {
		clearInterval(waitForDbConnections);
		return;
	}
	if (!dbClient) {
	    getDBClient();
	}
    if (!esHosts) {
        getESHosts();
    }}, 5000);

module.exports = router;
