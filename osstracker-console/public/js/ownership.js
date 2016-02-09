var users;
var repos;

$(document).ready(function() {
  $.get('/repos', function(data) {
    repos = data;
  })
  .fail(function() {
    alert("problem with loading repos data");
  });

  var substringMatcher = function(strs) {
    return function findMatches(q, cb) {
      var matches, substringRegex;

      // an array that will be populated with substring matches
      matches = [];

      // regex used to determine if a string contains the substring `q`
      substrRegex = new RegExp(q, 'i');

      // iterate through the pool of strings and for any string that
      // contains the substring `q`, add it to the `matches` array
      $.each(strs, function(i, str) {
        if (substrRegex.test(str)) {
          // the typeahead jQuery plugin expects suggestions to a
          // JavaScript object, refer to typeahead docs for more info
          matches.push({ value: str });
        }
      });

      cb(matches);
    };
  };

  var usersREST = "/users"
  var names = []
  $.get(usersREST, function(data) {
    users = data;
    
    $.each(data, function(i, item) {
      names.push(item.name);
    });
    
    $('#repoModalRepoDevLead').typeahead({
      hint: true,
      highlight: true,
      minLength: 1
    },
    {
      name: 'devNames',
      displayKey: 'value',
      source: substringMatcher(names)
    });
    
    $('#repoModalRepoMgrLead').typeahead({
      hint: true,
      highlight: true,
      minLength: 1
    },
    {
      name: 'mgrNames',
      displayKey: 'value',
      source: substringMatcher(names)
    });
  })
  .fail(function() {
    alert("problem with loading users data");
  });

  var orgsREST = "/repos/orgs";
  var orgs = []
  $.get(orgsREST, function(data) {
    $.each(data, function(i, item) {
      orgs.push(item.orgName);
     });

    $('#repoModalRepoOrg').typeahead({
      hint : true,
      highlight : true,
      minLength : 1
    }, {
      name : 'orgNames',
      displayKey : 'value',
      source : substringMatcher(orgs)
    });
  })
  .fail(function() {
    alert("problem with loading repos/orgs data");
  });

  $(document).ajaxStop(function() {
    $('#table').bootstrapTable({
      columns: [{
        field: 'name',
        title: 'Repo',
        sortable: true,
        formatter: repoLinkFormatter
      }, {
        field: 'orgName',
        title: 'OSS Area',
        sortable: true
      }, {
        field: 'mgrLead',
        title: 'Mgr Lead',
        sortable: true,
        formatter: mgrEmpIdToNameFormatter
      }, {
        field: 'devLead',
        title: 'Dev Lead',
        sortable: true,
        formatter: mgrEmpIdToNameFormatter
      }, {
        field: 'name',
        title: 'Edit',
        sortable: false,
        formatter: editLinkFormatter
      }],
      data: repos
    });

    $(window).resize(function () {
      $('#table').bootstrapTable('resetView');
     });
  });
});

function repoLinkFormatter(value, row) {
  return '<a href="http://www.github.com/netflix/' + value + '">' + value + '</a>'; 
}

function editLinkFormatter(value, row) {
    return '<a href="#" onclick="showRepoEdit(\'' + value + '\');">Edit</a>'; 
}

function mgrEmpIdToNameFormatter(value, row) {
    return getNameFromEmpId(value);
}

function showRepoEdit(repoName) {
  $('#repoModalRepoName').val(repoName);
  $('#repoModalRepoOrg').val(getRepoOrg(repoName));
  $('#repoModalRepoDevLead').val(getNameFromEmpId(getRepoDevLeadEmpIp(repoName)));
  $('#repoModalRepoMgrLead').val(getNameFromEmpId(getRepoMgrLeadEmpIp(repoName)));
  $('#clickRepoLinks').modal('show');
}

function updateRepository() {
    var repoName = $('#repoModalRepoName').val();
    var repoOrg = $('#repoModalRepoOrg').val();
    var repoMgrLead = $('#repoModalRepoMgrLead').val();
    var repoDevLead = $('#repoModalRepoDevLead').val();
    var mgrLead = getEmpIdFromName(repoMgrLead);
    var devLead = getEmpIdFromName(repoDevLead);
    
    if (mgrLead == -1 || devLead == -1) {
        $('#clickRepoLinks').modal('hide');
        $('#errorModalErrorText').text('Currently we only support known Netflix github users.  Add your github account to whitepages.');
        $('#errorModal').modal('show');
        return;
    }
    
    $.post(
        '/repos/' + repoName,
        { repoOrg: repoOrg, mgrLead: mgrLead, devLead: devLead},
        function(data) {
            console.log(data);
         }
    );
    $('#table').bootstrapTable('showLoading');
    loadReposData(function() {
        $('#table').data = repos;
        $('#table').bootstrapTable('hideLoading');
        $('#table').bootstrapTable('load', repos);
        $('#clickRepoLinks').modal('hide');
    })
}

function getGitHubUser(name) {
    var user = users.filter(function(item) {
        return item.name == name;
    })
    var githubId = user[0].githubId;
    return githubId;
}

function getRepoOrg(repoName) {
    var repo = repos.filter(function(item) {
        return item.name == repoName;
    })
    return repo[0].orgName;
}

function getRepoMgrLeadEmpIp(repoName) {
    var repo = repos.filter(function(item) {
        return item.name == repoName;
    })
    return repo[0].mgrLead;
}

function getRepoDevLeadEmpIp(repoName) {
    var repo = repos.filter(function(item) {
        return item.name == repoName;
    })
    return repo[0].devLead;
}

function getNameFromEmpId(id) {
    var emp = users.filter(function(item) {
        return item.employeeId == id;
    })
    if (emp.length == 0) {
        return id;
    }
    return emp[0].name;
}

function getEmpIdFromName(name) {
    var emp = users.filter(function(item) {
        return item.name == name;
    })
    if (emp.length == 0) {
        return -1;
    }
    return emp[0].employeeId;
}

function loadReposData(callback/*()*/) {
    $.get('/repos', function(data) {
        repos = data;
        callback();
    });
}