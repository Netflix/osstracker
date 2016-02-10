var esHost;

$(document).ready(function(){
  $.get('/repos/stats', function(data) {
    data = data.filter(function (elem) {
        return publicOrPrivate ? elem.public : !elem.public;
    })

    $('#statsTable').bootstrapTable({
      columns: [
        {
          field: 'name',
          title: 'Repo',
          sortable: true,
          formatter: repoLinkFormatter
        },
        {
          title: 'Graphs',
          formatter: esStatsFormatter
        },
        {
            field: 'forks',
            title: 'Forks',
            sortable: true
        },
        {
            field: 'stars',
            title: 'Stars',
            sortable: true
        },
        {
            field: 'issueOpenCount',
            title: 'Open Issues',
            sortable: true
        },
        {
            field: 'issueClosedCount',
            title: 'Closed Issues',
            sortable: true,
        },
        {
            field: 'issueAvgClose',
            title: 'Avg Issue Age',
            sortable: true
        },
        {
            field: 'prOpenCount',
            title: 'Open PRs',
            sortable: true
        },
        {
            field: 'prClosedCount',
            title: 'Closed PRs',
            sortable: true
        },
        {
            field: 'prAvgClose',
            title: 'Avg PR Age',
            sortable: true
        },
        {
            field: 'daysSinceLastCommit',
            title: 'Last Commit',
            sortable: true
        },
        {
            field: 'numContributors',
            title: 'Contributors',
            sortable: true
        },
        {
            field: 'public',
            title: 'Public',
            sortable: true
        },
        {
            field: 'osslifecycle',
            title: 'OSS Lifecycle',
            sortable: true
        },
      ],
      data: data
    });
    
    $(window).resize(function () {
        $('#statsTable').bootstrapTable('resetView');
        $('#tags').tagsinput('refresh');
    });
  });

  $.get('/hosts/eshost', function(data) {
    esHost = data;
  });

});

function repoLinkFormatter(value, row) {
    return '<a href="http://www.github.com/netflix/' + value + '">' + value + '</a>';
}

function esStatsFormatter(value, row) {
    return '<a href="http://' + esHost + ':7001/kibana/#/dashboard/elasticsearch/Netflix%20OSS Per Repo?query=repo_name:' +
        row['name'] + '"><span class="glyphicon glyphicon-signal"></span></a>';
}
