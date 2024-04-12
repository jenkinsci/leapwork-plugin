function GetSch() {
    var closestElement = (jQuery)(event.target).closest('.repeated-chunk');
    var closestEle;
    if (closestElement != null) {
        closestEle = closestElement[0].firstElementChild;
    } else {
        closestEle = document;
    }

    const leapworkHostname = (jQuery)(closestEle).find("#leapworkHostname").first().val();
    const leapworkPort = (jQuery)(closestEle).find("#leapworkPort").first().val();

    if (!leapworkHostname || !leapworkPort) {
        alert('"hostname or/and field is empty! Cannot connect to controller!"');
    } else {
        let myAddress = "";
        if ((jQuery)(closestEle).find("#chkEnableHttps").first().prop('checked')) {
            myAddress = "https://" + leapworkHostname + ":" + leapworkPort;
        } else {
            myAddress = "http://" + leapworkHostname + ":" + leapworkPort;
        }
        const address = myAddress;
        const accessKey = (jQuery)(closestEle).find("#leapworkAccessKey").first().val();

        if ((jQuery)(closestEle).find('#LeapworkContainer').first().html() == "") {
            (jQuery).ajax({
                url: address + "/api/v4/schedules/hierarchy",
                headers: { 'AccessKey': accessKey },
                type: 'GET',
                dataType: "json",
                success: function (json) {
                    const container = (jQuery)(closestEle).find('#LeapworkContainer').first();

                    (jQuery)(document).click(function (event) {
                        if ((jQuery)(event.target).closest('#LeapworkContainer').length == 0 && (jQuery)(event.target).attr('id') != 'mainButton') {
                            container.find("input:checkbox").remove();
                            container.find("li").remove();
                            container.find("ul").remove();
                            container.find("br").remove();
                            container.find("div").remove();
                            container.find("input").remove();
                            container.css('display', 'none');
                        }
                    });

                    const scheduleInfo = json.map((_) => _.ScheduleInfo);
                    const scheduleHierarchies = json.map((_) => _.ScheduleHierarchy);
                    createFolders(scheduleHierarchies, scheduleInfo, container);

                    function createFolders(ScheduleHeirarchies, scheduleInfo, root) {
                        for (const ScheduleHierarchy of ScheduleHeirarchies) {
                            const title = ScheduleHierarchy["Title"]
                            if (ScheduleHierarchy["Type"] == "Folder" && ScheduleHierarchy.Parent.length == 0) {
                                if (!document.getElementById(`${ScheduleHierarchy.Id}`))
                                    root.append(`<div id="${ScheduleHierarchy.Id}" class="Folder" style="color: red;">${title}</div>`);
                            } else if (ScheduleHierarchy["Type"] == "Folder" && ScheduleHierarchy.Parent.length) {
                                if (!document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`))
                                    createFolders(ScheduleHierarchy.Parent, scheduleInfo, root);
                                let myParent = document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`);
                                myParent.innerHTML += `<div id="${ScheduleHierarchy.Id}" class="Folder" style="color: darkblue; padding-left:20px;">${title}</div>`;
                            } else if (ScheduleHierarchy["Type"] == "RunList" && ScheduleHierarchy.Parent.length == 0) {
                                if (!document.getElementById(`${ScheduleHierarchy.Id}`))
                                    root.append(`<div id="${ScheduleHierarchy.Id}" class="RunList" style="color: black; padding-left:20px;">${title}</div>`);
                            } else if (ScheduleHierarchy["Type"] == "RunList") {
                                if (!document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`))
                                    createFolders(ScheduleHierarchy.Parent, scheduleInfo, root);
                                let myParent = document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`);
                                myParent.innerHTML += `<div id="${ScheduleHierarchy.Id}" class="RunList" style="color: black; padding-left:20px;">${title}</div>`;
                            } else if (ScheduleHierarchy["Type"] == "ScheduleInfo") {
                                var scheduleId = ScheduleHierarchy.Id;
                                if (!document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`))
                                    createFolders(ScheduleHierarchy.Parent, scheduleInfo, container);
                                let myParent = document.getElementById(`${ScheduleHierarchy.Parent[0].Id}`);
                                for (schedule of scheduleInfo) {
                                    if (schedule.Id == scheduleId) {
                                        if (schedule.IsEnabled) {
                                            myParent.innerHTML += `<div style="color: green; padding-left:30px;">
                                       <input type="checkBox" id="${ScheduleHierarchy.Id}" class="ScheduleInfo" name="${title}">${title}</div>`;
                                        } else {
                                            var element = `<div style="color:grey; padding-left:30px;">
                                        <input type="checkBox" disabled="true" id="${ScheduleHierarchy.Id}" class="ScheduleInfoDisabled" name="${title}"><s>${title}</s></div>`;
                                            myParent.innerHTML += element
                                        }
                                    }
                                }
                            }
                        }
                    }

                    container.append('<br>');
                    container.append('<br>');
                    container.css('display', 'block');
                    container.find(".ul-dropfree").find("li:has(ul)").prepend('<div class="drop"></div>');
                    container.find(".ul-dropfree div.drop").click(function () {
                        if ($(this).nextAll("ul").css('display') == 'none') {
                            $(this).nextAll("ul").slideDown(400);
                            $(this).css({ 'background-position': "-11px 0" });
                        } else {
                            $(this).nextAll("ul").slideUp(400);
                            $(this).css({ 'background-position': "0 0" });
                        }
                    });
                    container.find(".ul-dropfree").find("ul").slideUp(400).parents("li").children("div.drop").css({ 'background-position': "0 0" });

                    let TestNames = (jQuery)(closestEle).find("#schNames").first();
                    let TestIds = (jQuery)(closestEle).find("#schIds").first();
                    let boxes = (jQuery)(closestEle).find("#LeapworkContainer input:checkbox");
                    let existingTests = TestIds.val().split(/\r\n|\n|\s+,\s+|,\s+|\s+,|,/);
                    if (TestNames.val() !== null && TestIds.val() !== null) {
                        for (let i = 0; i < existingTests.length; i++) {
                            for (j = 0; j < boxes.length; j++) {
                                if (existingTests[i] == (jQuery)(boxes[j]).attr('id')) {
                                    if (!boxes[j].disabled)
                                        (jQuery)(boxes[j]).prop('checked', 'checked');
                                }
                            }
                        }
                    }
                    (jQuery)(closestEle).find("#LeapworkContainer input:checkbox").on("change", function () {
                        let NamesArray = [];
                        let IdsArray = [];
                        (jQuery)(boxes).each(function () {
                            if ((jQuery)(this).prop('checked')) {
                                NamesArray.push((jQuery)(this).attr('name'));
                                IdsArray.push((jQuery)(this).attr('id'));
                            }
                        });
                        TestNames.val(NamesArray.join("\n"));
                        TestIds.val(IdsArray.join("\n"));
                        console.log(TestIds.val());
                    });

                },
                error: function (XMLHttpRequest, textStatus, errorThrown) {
                    alert(
                        "Error occurred! Cannot get the list of Schedules!\n" +
                        "Status: " + textStatus + "\n" +
                        "Error: " + errorThrown + "\n" +
                        "This may occur because of the next reasons:\n" +
                        "1.Invalid controller hostname\n" +
                        "2.Invalid port number\n" +
                        "3.Invalid access key\n" +
                        "4.Controller is not running or updating now, check it in services\n" +
                        "5.Your Leapwork Controller API port is blocked.\nUse 'netstat -na | find \"9001\"' command, The result should be:\n 0.0.0.0:9001  0.0.0.0:0  LISTENING\n" +
                        "6.Your browser has such a setting enabled that blocks any http requests from https\n" +
                        "If nothing helps, please contact support https://leapwork.com/support"
                    );
                }
            });
        } else {
            container.find("input:checkbox").remove();
            container.find("li").remove();
            container.find("ul").remove();
            container.find("br").remove();
            container.find("div").remove();
            container.find("input").remove();
            GetSch();
        }
    }
}