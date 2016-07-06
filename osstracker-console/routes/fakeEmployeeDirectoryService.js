exports.getGithubIds = function(callback/*(err, githubIDs)*/) {
    var fakeResponse = [
        {"employeeId":"111111","githubId":"ghId1","email":"user1@netflix.com","name":"User One"},
        {"employeeId":"222222","githubId":"ghId2","email":"user2@netflix.com","name":"User Two"},
        {"employeeId":"333333","githubId":"ghId3","email":"user3@netflix.com","name":"User Three"}
    ];
    callback(null, fakeResponse);
}