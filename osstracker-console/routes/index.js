var express = require('express');
var log4js = require('log4js');
var request = require('request');
var cassandra = require('cassandra-driver');

var router = express.Router();

var CASS_HOST = process.env.CASS_HOST;
var CASS_PORT = parseInt(process.env.CASS_PORT) || 7104;
var ES_HOST = process.env.ES_HOST;
var ES_PORT = parseInt(process.env.ES_PORT) || 7104;

var logger = log4js.getLogger();
logger.setLevel('INFO');
var dbClient;
var esBaseUrl = 'http://' + ES_HOST + ':' + ES_PORT;

var SELECT_ALL_FROM_REPO_ORGS = "SELECT * FROM repo_orgs";
var INSERT_INTO_REPOS = "INSERT INTO repo_info (gh_repo_name, org_short, dev_lead_empid, mgr_lead_empid) VALUES (?, ?, ?, ?)";
var SELECT_ALL_FROM_REPO_OWNERSHIP = "SELECT * FROM repo_info";
var SELECT_REPOS_FROM_REPO_OWNERSHIP = "SELECT gh_repo_name FROM repo_info";

// returns a single string of what elastic search DNS name should
// be used in direct links in the console
router.get('/hosts/eshost', function(req, res, next) {
    res.send(ES_HOST);
});

// Response is JSON list that has repo items with repo name, repo org (short form),
// employee ids for manager lead and development lead
// [ { name: 'somerepo', orgName: 'CRSL', mgrLead: "12345", devLead: "56789" }, ... ]
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

//
// Response is JSON list that has users with name, github id, employee id, and email
// [ { employeeId: '123456', githubId: 'githubusername', email: 'user@netflix.com', name: 'First Last' }, ... ]
router.get('/users', function(req, res, next) {
    var fakeResponse = [
        {"employeeId":"111111","githubId":"ghId1","email":"user1@netflix.com","name":"User One"},
        {"employeeId":"222222","githubId":"ghId2","email":"user2@netflix.com","name":"User Two"}
    ];
    res.send(fakeResponse);
    // TODO: Generalize the below to a more pluggable employee whitepages implementation
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

// Response is JSON list that has orgs with long and short name
// [ {"orgName":"DP","orgDesc":"Data Persistence"} , {"orgName":"BDT","orgDesc":"Build and Delivery Tools"}, ... ]
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

// Request to update the ownership of a repository
// Expects repoName, repoOrg, mgrLead (employee id ), devLead (employee id)
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
//Response is JSON list that has repo stats with various feilds of format
// [ {
//   "name":"repoName","forks":100,"stars":200,"numContributors":20,"issueOpenCount":10,"issueClosedCount":300,
//   "issueAvgClose":13,"prOpenCount":8,"prClosedCount":259,"prAvgClose":3,"daysSinceLastCommit":59,"public":true,
//   "osslifecycle":"active"}, ... ]
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

// Response is a single elasticsearch document with the stats from each project
// format is:
// {"asOfISO":"2016-02-09T08:18:44Z","asOfYYYYMMDD":"2016-02-09","avgForks":134,"avgStars":599,
//   "issues":{ "avgOpenCount":39,"avgClosedCount":210,"totalOpenCount":356,"totalClosedCount":1897},
//   "pullRequests":{"avgOpenCount":8,"avgClosedCount":154,"totalOpenCount":73,"totalClosedCount":1389},
//   "commits":{},
//   "repos":[
//     {"asOfISO":"2016-02-09T08:18:44Z","asOfYYYYMMDD":"2016-02-09","repo_name":"repoName","public":true,
//      "osslifecycle":"active","forks":172,"stars":821,"numContributors":25,
//        "issues": {"openCount":68,"closedCount":323,"avgTimeToCloseInDays":13},
//        "pullRequests":{"openCount":8,"closedCount":259,"avgTimeToCloseInDays":3},
//        "commits":{"daysSinceLastCommit":59},"contributors":["user", ... ]
//      }, ...
//   ]
// }
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

function queryAllStats(repoName, callback/*(err, hits)*/) {
    // query to search for a specific repo returning only the last document (date wise)
    var query = { "size": 1, "sort": [{"asOfYYYYMMDD": {"order": "desc"}}]};
    var url = esBaseUrl + '/osstracker/allrepos_stats/_search';
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
    // query to search for a specific repo returning only the last document (date wise)
    var query = { "size": 1, "sort": [{"asOfYYYYMMDD": {"order": "desc"}}]};
    var url = esBaseUrl + '/osstracker/allrepos_stats/_search';

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
	client = new cassandra.Client({ contactPoints: hosts, protocolOptions : { port : CASS_PORT }, keyspace: 'osstracker'});
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

var waitForDbConnections = setInterval(function () {
	if (dbClient) {
		clearInterval(waitForDbConnections);
		return;
	}
	else {
	    getDBClient();
	}
}, 5000);

module.exports = router;
